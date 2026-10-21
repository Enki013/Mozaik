package com.personalrecords.app.core.receiver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SET_CLIPBOARD) {
            val text = intent.getStringExtra(EXTRA_TEXT) ?: return
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", text)
            clipboard.setPrimaryClip(clip)

            val showToast = intent.getBooleanExtra(EXTRA_SHOW_TOAST, true)
            if (showToast) {
                val preview = if (text.length > 24) text.take(24) + "..." else text
                Toast.makeText(context, "Panoya kopyalandı: $preview", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_SET_CLIPBOARD = "com.personalrecords.app.SET_CLIPBOARD"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SHOW_TOAST = "show_toast"
    }
}
