package com.ultron.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Watches the phone ringing / in a call / idle. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android sends this twice; with call-log permission only the second one has the number
                if (number == null && Phone.has(context, Manifest.permission.READ_CALL_LOG)) return
                if (CallState.ringing.value != null) return
                val name = Contacts.nameFor(context, number)
                val who = name ?: number?.let { spaced(it) } ?: "an unknown number"
                CallState.inCall.value = false
                if (Dnd.isOn(context) && Dnd.onCall(context, number, name)) return
                CallState.ringing.value = who
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallState.ringing.value = null
                CallState.inCall.value = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallState.ringing.value = null
                CallState.inCall.value = false
            }
        }
    }

    private fun spaced(n: String) = n.filter { it.isDigit() }.chunked(1).joinToString(" ")
}

object CallState {
    /** Who is calling right now (null when not ringing). */
    val ringing = MutableStateFlow<String?>(null)
    val inCall = MutableStateFlow(false)
}
