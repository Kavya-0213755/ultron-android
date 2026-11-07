package com.ultron.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Approve" / "Deny" buttons on the notification. */
class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        UltronApp.instance.client.answer(id, intent.getBooleanExtra("approve", false))
    }
}
