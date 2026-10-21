package com.cleanurl.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.cleanurl.app.core.ClipboardHelper
import com.cleanurl.app.core.LinkProcessor
import com.cleanurl.app.data.AppPreferences
import com.cleanurl.app.data.HistoryEntry
import kotlinx.coroutines.launch

/**
 * Fully transparent, UI-less receiver for the system share sheet. Cleans the shared link,
 * writes it to the clipboard and finishes — the user never leaves the app they shared from.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }?.let {
            it.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: it.getStringExtra(Intent.EXTRA_SUBJECT)
        }
        if (sharedText.isNullOrBlank()) {
            finishWithToast(getString(R.string.toast_no_url))
            return
        }

        val prefs = AppPreferences.get(this)
        lifecycleScope.launch {
            val settings = prefs.settings.value
            val result = LinkProcessor().process(sharedText, settings.toCleanOptions())
            if (result == null) {
                finishWithToast(getString(R.string.toast_no_url))
                return@launch
            }
            ClipboardHelper.write(this@ShareReceiverActivity, result.cleaned)
            prefs.addHistory(HistoryEntry(result.platform, result.cleaned, System.currentTimeMillis()))
            ClipboardHelper.notifyCopied(
                this@ShareReceiverActivity,
                getString(R.string.toast_copied),
                settings.toastOnAndroid13,
            )
            finishSilently()
        }
    }

    private fun finishWithToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finishSilently()
    }

    private fun finishSilently() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
