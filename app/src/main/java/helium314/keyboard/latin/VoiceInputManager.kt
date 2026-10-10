/*
 * Continuous voice typing owned by the FrostKeys IME.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import java.util.Locale

/** Runs speech recognition in the IME process without launching a speech-input Activity. */
class VoiceInputManager(
    private val service: InputMethodService,
    private val layoutLocaleProvider: () -> Locale,
    private val listener: Listener,
) {
    enum class State { STOPPED, INITIALIZING, LISTENING, PROCESSING }

    interface Listener {
        fun onStateChanged(state: State, locale: Locale)
        fun onAudioLevelChanged(level: Float)
        fun onPermissionRequired()
        fun onError(message: String)
    }

    companion object {
        private const val PERMISSION_RETURN_DELAY_MILLIS = 350L
        private const val MANUAL_STOP_TIMEOUT_MILLIS = 2_500L
        private const val END_OF_SPEECH_TIMEOUT_MILLIS = 8_000L
        private const val RMS_MIN_DB = -10f
        private const val RMS_MAX_DB = 10f
        private const val RMS_LERP_FACTOR = 0.25f

        /** Maps the keyboard language to the requested ASR locale, retaining other layout locales. */
        @JvmStatic
        fun resolveLanguageTag(layoutLocale: Locale?): String {
            when (layoutLocale?.language?.lowercase(Locale.ROOT)) {
                "ar" -> return "ar-EG"
                "en" -> return "en-US"
                "fr" -> return "fr-FR"
                "de" -> return "de-DE"
            }
            val fallback = layoutLocale?.takeIf {
                it != Locale.ROOT && it.language.isNotBlank() && it.toLanguageTag() != "und"
            } ?: Locale.getDefault()
            return fallback.toLanguageTag().takeIf { it.isNotBlank() && it != "und" }
                ?: Locale.getDefault().toLanguageTag()
        }

        @JvmStatic
        fun normalizeRmsDb(rmsDb: Float): Float {
            if (!rmsDb.isFinite()) return 0f
            return ((rmsDb - RMS_MIN_DB) / (RMS_MAX_DB - RMS_MIN_DB)).coerceIn(0f, 1f)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentState = State.STOPPED
    private var currentLocale = Locale.getDefault()
    private var activeInputConnection: InputConnection? = null
    private var isActive = false
    private var isDestroyed = false
    private var isWaitingForPermission = false
    private var isManualStopWaitingForFinal = false
    private var requestInFlight = false
    private var awaitingFinalResult = false
    private var leadingSpaceForUtterance = false
    private var latestPartialTranscript = ""
    private var smoothedRms = 0f
    private var sessionToken = 0

    val isPermissionRequestPending: Boolean
        get() = isWaitingForPermission

    private val manualStopTimeout = Runnable {
        if (isManualStopWaitingForFinal) {
            finishSession(cancelRecognizer = true)
        }
    }

    private val endOfSpeechTimeout = Runnable {
        if (isActive && awaitingFinalResult) {
            stopWithError(service.getString(R.string.voice_input_error))
        }
    }

    fun toggle() = onMainThread {
        if (isActive) stopByUser() else start()
    }

    /** Called by the permission Activity after the user responds to Android's permission dialog. */
    fun onPermissionResult(granted: Boolean) = onMainThread {
        if (!isWaitingForPermission || isDestroyed) return@onMainThread
        if (granted) {
            mainHandler.postDelayed({
                isWaitingForPermission = false
                start()
            }, PERMISSION_RETURN_DELAY_MILLIS)
        } else {
            isWaitingForPermission = false
            listener.onError(service.getString(R.string.voice_input_permission_required))
        }
    }

    /** Stop recognition and finalize any in-progress preview when the editor or IME view is leaving. */
    fun stopAndDiscard() = onMainThread {
        if (!isActive && speechRecognizer == null && !isWaitingForPermission)
            return@onMainThread
        sessionToken++
        isActive = false
        isManualStopWaitingForFinal = false
        isWaitingForPermission = false
        removePendingCallbacks()
        finishInputComposition()
        latestPartialTranscript = ""
        requestInFlight = false
        awaitingFinalResult = false
        activeInputConnection = null
        releaseRecognizer(cancel = true)
        setAudioLevel(0f)
        setState(State.STOPPED)
    }

    /** Release the recognizer with the InputMethodService. */
    fun destroy() = onMainThread {
        isDestroyed = true
        stopAndDiscard()
        releaseRecognizer(cancel = true)
    }

    private fun start() {
        if (isDestroyed || isActive || isWaitingForPermission) return
        if (isManualStopWaitingForFinal) return
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            isWaitingForPermission = true
            listener.onPermissionRequired()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            listener.onError(service.getString(R.string.voice_input_unavailable))
            return
        }
        val connection = service.currentInputConnection
        if (connection == null) {
            listener.onError(service.getString(R.string.voice_input_error))
            return
        }

        currentLocale = runCatching(layoutLocaleProvider).getOrElse { Locale.getDefault() }
        activeInputConnection = connection
        isActive = true
        latestPartialTranscript = ""
        sessionToken++
        if (!createRecognizer()) {
            isActive = false
            activeInputConnection = null
            setState(State.STOPPED)
            listener.onError(service.getString(R.string.voice_input_unavailable))
            return
        }
        startListening()
    }

    private fun createRecognizer(): Boolean {
        if (speechRecognizer != null) return true
        val token = sessionToken
        return try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(service)
            recognizer.setRecognitionListener(object : RecognitionListener {
                private fun isCurrentSession() = token == sessionToken

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrentSession() || !isActive) return
                    awaitingFinalResult = false
                    setState(State.LISTENING)
                }

                override fun onBeginningOfSpeech() {
                    if (isCurrentSession() && isActive) setState(State.LISTENING)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (!isCurrentSession() || !isActive) return
                    val target = normalizeRmsDb(rmsdB)
                    smoothedRms += (target - smoothedRms) * RMS_LERP_FACTOR
                    setAudioLevel(smoothedRms)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (!isCurrentSession() || !isActive) return
                    awaitingFinalResult = true
                    setState(State.PROCESSING)
                    mainHandler.removeCallbacks(endOfSpeechTimeout)
                    mainHandler.postDelayed(endOfSpeechTimeout, END_OF_SPEECH_TIMEOUT_MILLIS)
                }

                override fun onError(error: Int) {
                    if (isCurrentSession()) handleRecognitionError(error)
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrentSession() || (!isActive && !isManualStopWaitingForFinal)) return
                    mainHandler.removeCallbacks(endOfSpeechTimeout)
                    requestInFlight = false
                    awaitingFinalResult = false
                    setState(State.PROCESSING)
                    val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    commitFinalTranscript(recognized.ifBlank { latestPartialTranscript })
                    latestPartialTranscript = ""
                    finishSession(cancelRecognizer = false)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrentSession() || !isActive) return
                    val partialText = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().trim()
                    latestPartialTranscript = partialText
                    // An empty result with no active composing range could replace a selected
                    // document range with nothing, so only update when there is recognized text.
                    if (partialText.isNotEmpty()) {
                        val prefix = if (leadingSpaceForUtterance && partialText.firstOrNull()?.isWhitespace() == false) " " else ""
                        activeInputConnection?.setComposingText(prefix + partialText, 1)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speechRecognizer = recognizer
            true
        } catch (_: RuntimeException) {
            speechRecognizer = null
            false
        }
    }

    private fun startListening() {
        val recognizer = speechRecognizer
            ?: return stopWithError(service.getString(R.string.voice_input_unavailable))
        if (!isActive || requestInFlight) return
        val connection = activeInputConnection ?: run {
            stopAndDiscard()
            return
        }
        // Keep an existing editor composition separate; otherwise the first voice composition
        // could replace the user's unfinished typed text.
        connection.finishComposingText()
        currentLocale = runCatching(layoutLocaleProvider).getOrElse { Locale.getDefault() }
        leadingSpaceForUtterance = needsLeadingSpace(connection)
        setAudioLevel(0f)
        setState(State.INITIALIZING)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolveLanguageTag(currentLocale))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, service.packageName)
        }
        requestInFlight = true
        awaitingFinalResult = false
        try {
            // This Intent is consumed by SpeechRecognizer; it is never launched as an Activity.
            recognizer.startListening(intent)
        } catch (_: RuntimeException) {
            requestInFlight = false
            handleRecognitionError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun commitFinalTranscript(transcript: String) {
        val connection = activeInputConnection ?: return
        val text = transcript.trim()
        if (text.isEmpty()) return
        val prefix = if (leadingSpaceForUtterance && text.firstOrNull()?.isWhitespace() == false) " " else ""
        val batchStarted = connection.beginBatchEdit()
        try {
            connection.commitText(prefix + text, 1)
        } finally {
            if (batchStarted) connection.endBatchEdit()
        }
    }

    private fun needsLeadingSpace(connection: InputConnection): Boolean {
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false
        val beforeCursor = connection.getTextBeforeCursor(1, 0) ?: return false
        return beforeCursor.isNotEmpty() && !beforeCursor.last().isWhitespace()
    }

    private fun finishInputComposition() {
        activeInputConnection?.finishComposingText()
    }

    private fun handleRecognitionError(error: Int) {
        mainHandler.removeCallbacks(endOfSpeechTimeout)
        requestInFlight = false
        awaitingFinalResult = false
        if (isManualStopWaitingForFinal) {
            finishSession(cancelRecognizer = true)
            return
        }
        if (!isActive) return
        setState(State.PROCESSING)
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                service.getString(R.string.voice_input_permission_required)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                service.getString(R.string.voice_input_language_unavailable)
            else -> service.getString(R.string.voice_input_error)
        }
        stopWithError(message)
    }

    private fun stopByUser() {
        if (!isActive) return
        isActive = false
        isManualStopWaitingForFinal = true
        setAudioLevel(0f)
        setState(State.PROCESSING)
        mainHandler.removeCallbacks(manualStopTimeout)
        mainHandler.postDelayed(manualStopTimeout, MANUAL_STOP_TIMEOUT_MILLIS)
        if (requestInFlight && !awaitingFinalResult) {
            runCatching { speechRecognizer?.stopListening() }
        } else if (!requestInFlight) {
            finishSession(cancelRecognizer = true)
        }
    }

    private fun finishSession(cancelRecognizer: Boolean) {
        mainHandler.removeCallbacks(manualStopTimeout)
        removePendingCallbacks()
        finishInputComposition()
        isManualStopWaitingForFinal = false
        isActive = false
        requestInFlight = false
        awaitingFinalResult = false
        latestPartialTranscript = ""
        activeInputConnection = null
        sessionToken++
        releaseRecognizer(cancel = cancelRecognizer)
        setAudioLevel(0f)
        setState(State.STOPPED)
    }

    private fun stopWithError(message: String) {
        finishSession(cancelRecognizer = true)
        listener.onError(message)
    }

    private fun releaseRecognizer(cancel: Boolean) {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        if (cancel) runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }

    private fun removePendingCallbacks() {
        mainHandler.removeCallbacks(manualStopTimeout)
        mainHandler.removeCallbacks(endOfSpeechTimeout)
    }

    private fun setState(state: State) {
        if (currentState == state) return
        currentState = state
        listener.onStateChanged(state, currentLocale)
    }

    private fun setAudioLevel(level: Float) {
        smoothedRms = level.coerceIn(0f, 1f)
        listener.onAudioLevelChanged(smoothedRms)
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
