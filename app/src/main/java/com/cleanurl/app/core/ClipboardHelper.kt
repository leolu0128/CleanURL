package com.cleanurl.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

object ClipboardHelper {

    fun write(context: Context, text: String) {
        clipboard(context).setPrimaryClip(ClipData.newPlainText("CleanURL", text))
    }

    /** Only works while the app has window focus (Android 10+). */
    fun read(context: Context): String? {
        val clip = clipboard(context).primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    /**
     * Android 13+ shows its own clipboard preview, so the toast there is opt-in;
     * older versions always get a toast.
     */
    fun notifyCopied(context: Context, message: String, toastOnAndroid13: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || toastOnAndroid13) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clipboard(context: Context) =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
