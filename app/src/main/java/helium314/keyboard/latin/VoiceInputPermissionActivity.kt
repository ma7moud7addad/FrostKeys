/*
 * Runtime microphone permission request launched from the IME.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.Manifest

/** Hosts Android's required runtime permission prompt; it does not perform speech recognition. */
class VoiceInputPermissionActivity : Activity() {
    private var didReportResult = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 7391
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            reportResult(true)
        } else if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    @Deprecated("Deprecated in Android, retained for the platform permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RECORD_AUDIO) return
        reportResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    @Deprecated("Deprecated in Android, retained for the platform back callback")
    override fun onBackPressed() {
        reportResult(false)
    }

    private fun reportResult(granted: Boolean) {
        if (didReportResult) return
        didReportResult = true
        LatinIME.getInstance()?.onVoiceInputPermissionResult(granted)
        finish()
    }
}
