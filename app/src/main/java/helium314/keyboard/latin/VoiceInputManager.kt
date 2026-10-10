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
        private const val RESTART_DELAY_MILLIS = 250L
        private const val PERMISSION_RETURN_DELAY_MILLIS = 350L
        private const val MANUAL_STOP_TIMEOUT_MILLIS = 2_500L
        private const val END_OF_SPEECH_TIMEOUT_MILLIS = 8_000L
        private const val TRANSIENT_RETRY_DELAY_MILLIS = 700L
        private const val MAX_TRANSIENT_RETRIES = 4
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
    private var hasComposingPreview = false
    private var leadingSpaceForUtterance = false
    private var latestPartialTranscript = ""
    private var smoothedRms = 0f
    private var sessionToken = 0
    private var transientErrorCount = 0
    private var restartRunnable: Runnable? = null

    val isPermissionRequestPending: Boolean
        get() = isWaitingForPermission

    private val manualStopTimeout = Runnable {
        if (isManualStopWaitingForFinal) {
            commitTranscript(latestPartialTranscript)
            finishManualStop()
        }
    }

    private val endOfSpeechTimeout = Runnable {
        if (isActive && awaitingFinalResult) {
            // Some recognition providers fail to deliver a final callback after end-of-speech.
            // Recreate the recognizer before retrying so we don't call startListening while busy.
            sessionToken++
            releaseRecognizer(cancel = true)
            requestInFlight = false
            awaitingFinalResult = false
            clearComposingPreview()
            latestPartialTranscript = ""
            if (createRecognizer()) scheduleListening(RESTART_DELAY_MILLIS)
            else stopWithError(service.getString(R.string.voice_input_unavailable))
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

    /** Stop and discard an in-progress preview when the editor or IME view is leaving. */
    fun stopAndDiscard() = onMainThread {
        if (!isActive && speechRecognizer == null && !hasComposingPreview && !isWaitingForPermission)
            return@onMainThread
        sessionToken++
        isActive = false
        isManualStopWaitingForFinal = false
        isWaitingForPermission = false
        removePendingCallbacks()
        clearComposingPreview()
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
        transientErrorCount = 0
        latestPartialTranscript = ""
        hasComposingPreview = false
        sessionToken++
        if (!createRecognizer()) {
            isActive = false
            activeInputConnection = null
            setState(State.STOPPED)
            listener.onError(service.getString(R.string.voice_input_unavailable))
            return
        }
        scheduleListening(0L)
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
                    transientErrorCount = 0
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
                    // SpeechRecognizer requires waiting for onResults/onError before the next
                    // startListening call. onEndOfSpeech switches the UI to Processing; the
                    // continuous loop restarts safely as soon as the final callback arrives.
                }

                override fun onError(error: Int) {
                    if (isCurrentSession()) handleRecognitionError(error)
                }

                override fun onResults(results: Bundle?) {
                    if (!isCurrentSession()) return
                    mainHandler.removeCallbacks(endOfSpeechTimeout)
                    requestInFlight = false
                    awaitingFinalResult = false
                    if (isActive) setState(State.PROCESSING)
                    val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    commitTranscript(recognized.ifBlank { latestPartialTranscript })
                    latestPartialTranscript = ""
                    hasComposingPreview = false
                    transientErrorCount = 0
                    if (isManualStopWaitingForFinal) {
                        finishManualStop()
                    } else if (isActive) {
                        scheduleListening(RESTART_DELAY_MILLIS)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isCurrentSession() || !isActive) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().trim()
                    if (partial.isNotEmpty()) {
                        latestPartialTranscript = partial
                        showComposingPreview(partial)
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

    private fun scheduleListening(delayMillis: Long) {
        if (!isActive) return
        restartRunnable?.let(mainHandler::removeCallbacks)
        val token = sessionToken
        val runnable = Runnable {
            restartRunnable = null
            if (isActive && token == sessionToken) startListening()
        }
        restartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: run {
            if (!createRecognizer()) stopWithError(service.getString(R.string.voice_input_unavailable))
            return
        }
        if (!isActive || requestInFlight) return
        val connection = activeInputConnection ?: run {
            stopAndDiscard()
            return
        }
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

    private fun showComposingPreview(partial: String) {
        val connection = activeInputConnection ?: return
        val prefix = if (leadingSpaceForUtterance && partial.firstOrNull()?.isWhitespace() == false) " " else ""
        if (connection.setComposingText(prefix + partial, 1)) hasComposingPreview = true
    }

    private fun commitTranscript(transcript: String) {
        val connection = activeInputConnection ?: return
        val text = transcript.trim()
        if (text.isEmpty()) {
            clearComposingPreview()
            return
        }
        val prefix = if (leadingSpaceForUtterance) " " else ""
        // Final speech is committed directly to the active editor. If partial text was composing,
        // commitText replaces that composing span instead of duplicating the preview.
        connection.commitText(prefix + text, 1)
        hasComposingPreview = false
    }

    private fun needsLeadingSpace(connection: InputConnection): Boolean {
        if (!connection.getSelectedText(0).isNullOrEmpty()) return false
        val beforeCursor = connection.getTextBeforeCursor(1, 0) ?: return false
        return beforeCursor.isNotEmpty() && !beforeCursor.last().isWhitespace()
    }

    private fun clearComposingPreview() {
        if (!hasComposingPreview) return
        activeInputConnection?.let { connection ->
            connection.setComposingText("", 1)
            connection.finishComposingText()
        }
        hasComposingPreview = false
    }

    private fun handleRecognitionError(error: Int) {
        mainHandler.removeCallbacks(endOfSpeechTimeout)
        requestInFlight = false
        awaitingFinalResult = false
        if (isManualStopWaitingForFinal) {
            commitTranscript(latestPartialTranscript)
            latestPartialTranscript = ""
            finishManualStop()
            return
        }
        if (!isActive) return

        clearComposingPreview()
        latestPartialTranscript = ""
        setState(State.PROCESSING)
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stopWithError(service.getString(R.string.voice_input_permission_required))
            }
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                stopWithError(service.getString(R.string.voice_input_language_unavailable))
            }
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleListening(RESTART_DELAY_MILLIS)
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                if (transientErrorCount++ < MAX_TRANSIENT_RETRIES) {
                    scheduleListening(TRANSIENT_RETRY_DELAY_MILLIS * transientErrorCount)
                } else {
                    stopWithError(service.getString(R.string.voice_input_error))
                }
            }
            else -> stopWithError(service.getString(R.string.voice_input_error))
        }
    }

    private fun stopByUser() {
        if (!isActive) return
        isActive = false
        isManualStopWaitingForFinal = true
        restartRunnable?.let(mainHandler::removeCallbacks)
        restartRunnable = null
        setAudioLevel(0f)
        setState(State.STOPPED)
        mainHandler.removeCallbacks(manualStopTimeout)
        mainHandler.postDelayed(manualStopTimeout, MANUAL_STOP_TIMEOUT_MILLIS)
        if (requestInFlight && !awaitingFinalResult) {
            runCatching { speechRecognizer?.stopListening() }
        } else if (!requestInFlight) {
            commitTranscript(latestPartialTranscript)
            finishManualStop()
        }
    }

    private fun finishManualStop() {
        mainHandler.removeCallbacks(manualStopTimeout)
        mainHandler.removeCallbacks(endOfSpeechTimeout)
        isManualStopWaitingForFinal = false
        isActive = false
        requestInFlight = false
        awaitingFinalResult = false
        latestPartialTranscript = ""
        activeInputConnection = null
        sessionToken++
        releaseRecognizer(cancel = false)
        setAudioLevel(0f)
        setState(State.STOPPED)
    }

    private fun stopWithError(message: String) {
        isActive = false
        isManualStopWaitingForFinal = false
        requestInFlight = false
        awaitingFinalResult = false
        restartRunnable?.let(mainHandler::removeCallbacks)
        restartRunnable = null
        mainHandler.removeCallbacks(endOfSpeechTimeout)
        clearComposingPreview()
        latestPartialTranscript = ""
        activeInputConnection = null
        sessionToken++
        releaseRecognizer(cancel = true)
        setAudioLevel(0f)
        setState(State.STOPPED)
        listener.onError(message)
    }

    private fun releaseRecognizer(cancel: Boolean) {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        if (cancel) runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }

    private fun removePendingCallbacks() {
        restartRunnable?.let(mainHandler::removeCallbacks)
        restartRunnable = null
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
