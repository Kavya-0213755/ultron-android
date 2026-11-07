package com.ultron.assistant

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DND mode: messages get a polite auto-reply (once per person), calls are declined with an SMS
 * (favourite contacts can still ring), and Ultron tells you what you missed when you're back.
 */
object Dnd {
    const val DEFAULT_REPLY = "Hi! I'm busy right now and can't reply. I'll get back to you soon. (auto-reply)"

    val on = MutableStateFlow(false)
    private val replied = HashSet<String>()
    private val missed = ArrayList<String>()
    private var weTurnedOnSystemDnd = false

    fun isOn(ctx: Context): Boolean {
        if (!on.value && UltronApp.instance.prefs.dnd) on.value = true          // survives a restart
        return on.value
    }

    fun start(ctx: Context, text: String?): String {
        val prefs = UltronApp.instance.prefs
        if (!text.isNullOrBlank()) prefs.dndReply = text.trim()
        prefs.dnd = true
        on.value = true
        synchronized(this) { replied.clear(); missed.clear() }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        weTurnedOnSystemDnd = false
        if (prefs.dndSilence && nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            weTurnedOnSystemDnd = true
        }
        val notes = mutableListOf<String>()
        if (!NotifListener.enabled(ctx)) notes += "turn on notification access for message auto-replies"
        if (!Phone.has(ctx, android.Manifest.permission.ANSWER_PHONE_CALLS)) notes += "allow phone calls for declining calls"
        return "DND is on. I'll reply “${prefs.dndReply}”" +
            (if (prefs.dndStarredRing) ", and your favourite contacts can still ring." else ".") +
            (if (notes.isNotEmpty()) " (In the Ultron app settings: ${notes.joinToString(" and ")}.)" else "")
    }

    fun stop(ctx: Context): String {
        val prefs = UltronApp.instance.prefs
        prefs.dnd = false
        on.value = false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (weTurnedOnSystemDnd && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        weTurnedOnSystemDnd = false
        val list = synchronized(this) { missed.toList().also { missed.clear(); replied.clear() } }
        if (list.isEmpty()) return "DND is off. Nothing came in while you were busy."
        val shown = list.takeLast(12)
        return "DND is off. While you were busy: " + shown.joinToString("; ") +
            (if (list.size > shown.size) " — and ${list.size - shown.size} more." else ".")
    }

    private fun time() = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())

    fun onMessage(ctx: Context, m: InMsg, n: Notification) {
        synchronized(this) { missed += "${m.app} from ${m.sender} at ${time()}: “${m.text.take(120)}”" }
        if (m.group || m.app == "Gmail") return                                  // never auto-reply in groups
        val key = m.pkg + "|" + m.sender.lowercase()
        synchronized(this) { if (!replied.add(key)) return }
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true } ?: return
        NotifListener.reply(ctx, action, UltronApp.instance.prefs.dndReply)
    }

    /** Called when the phone rings in DND. Returns true if the call was declined. */
    fun onCall(ctx: Context, number: String?, name: String?): Boolean {
        val prefs = UltronApp.instance.prefs
        val who = name ?: number ?: "an unknown number"
        if (prefs.dndStarredRing && Contacts.isStarred(ctx, number)) {
            synchronized(this) { missed += "a call from $who at ${time()} (let through)" }
            return false
        }
        val declined = Phone.endCall(ctx)
        synchronized(this) { missed += "a call from $who at ${time()}" + if (declined) " (declined)" else "" }
        if (declined && !number.isNullOrBlank()) {
            val key = "call|$number"
            if (synchronized(this) { replied.add(key) }) Phone.sendSms(ctx, number, prefs.dndReply)
        }
        return declined
    }

    @Suppress("DEPRECATION")
    fun smsManager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
}
