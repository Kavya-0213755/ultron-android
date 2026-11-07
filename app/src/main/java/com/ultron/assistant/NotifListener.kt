package com.ultron.assistant

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class InMsg(
    val app: String, val pkg: String, val sender: String, val text: String, val time: Long, val group: Boolean,
)

/**
 * Sees incoming messages (WhatsApp, SMS, Telegram, Instagram…) through notification access, so Ultron can
 * read them out, reply to them and auto-reply in DND mode.
 */
class NotifListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val msgs = parse(this, sbn) ?: return
        val latest = msgs.lastOrNull() ?: return
        if (System.currentTimeMillis() - latest.time > 5 * 60_000) return       // old message re-posted
        val key = "${latest.pkg}|${latest.sender}|${latest.text}"
        synchronized(seen) {
            if (!seen.add(key)) return
            if (seen.size > 500) seen.clear()
        }
        remember(latest)
        findReply(sbn.notification)?.let { replies[latest.pkg + "|" + latest.sender.lowercase()] = it }

        val app = UltronApp.instance
        if (Dnd.isOn(app)) {
            Dnd.onMessage(this, latest, sbn.notification)
        } else if (app.prefs.announceMessages && !latest.group) {
            scope.launch { app.speaker.say("${latest.app} message from ${latest.sender}: ${latest.text}") }
        }
    }

    companion object {
        @Volatile var instance: NotifListener? = null
        private val seen = LinkedHashSet<String>()
        private val recent = ArrayDeque<InMsg>()
        val replies = HashMap<String, Notification.Action>()

        val APPS = mapOf(
            "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business",
            "com.google.android.apps.messaging" to "SMS", "com.samsung.android.messaging" to "SMS",
            "com.android.mms" to "SMS", "com.android.messaging" to "SMS", "com.oneplus.mms" to "SMS",
            "org.telegram.messenger" to "Telegram", "com.instagram.android" to "Instagram",
            "com.facebook.orca" to "Messenger", "org.thoughtcrime.securesms" to "Signal",
            "com.truecaller" to "SMS", "com.google.android.gm" to "Gmail",
        )

        fun enabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.contains(ComponentName(ctx, NotifListener::class.java).flattenToString())
        }

        private fun remember(m: InMsg) = synchronized(recent) {
            recent.addLast(m)
            while (recent.size > 200) recent.removeFirst()
        }

        fun recent(n: Int): List<InMsg> = synchronized(recent) { recent.toList().takeLast(n) }

        /** Messages still waiting in the notification shade (= unread), oldest first. */
        fun unread(ctx: Context): List<InMsg> {
            val l = instance ?: return emptyList()
            val all = try { l.activeNotifications?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            return all.flatMap { parse(ctx, it) ?: emptyList() }.sortedBy { it.time }
        }

        fun parse(ctx: Context, sbn: StatusBarNotification): List<InMsg>? {
            if (sbn.packageName == ctx.packageName) return null
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && sbn.packageName != "com.google.android.gm") return null
            val known = APPS[sbn.packageName]
            if (known == null && n.category != Notification.CATEGORY_MESSAGE) return null
            val appName = known ?: try {
                ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (_: Exception) { sbn.packageName }
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            if (style != null && style.messages.isNotEmpty()) {
                val group = style.isGroupConversation
                val title = style.conversationTitle?.toString()
                return style.messages.mapNotNull { m ->
                    val text = m.text?.toString() ?: return@mapNotNull null
                    val who = m.person?.name?.toString() ?: title
                        ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "someone"
                    val sender = if (group && title != null) "$who in $title" else who
                    InMsg(appName, sbn.packageName, sender, text, if (m.timestamp > 0) m.timestamp else sbn.postTime, group)
                }.takeLast(10)
            }
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
            val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return null
            if (Regex("^\\d+ new messages?$|messages? from \\d+ chats?", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
            return listOf(InMsg(appName, sbn.packageName, title, text, sbn.postTime, false))
        }

        private fun findReply(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

        /** Reply through the notification's own "Reply" button (works for WhatsApp, SMS, Telegram…). */
        fun reply(ctx: Context, action: Notification.Action, text: String): Boolean = try {
            val intent = Intent()
            val bundle = Bundle()
            action.remoteInputs.forEach { bundle.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            action.actionIntent.send(ctx, 0, intent)
            true
        } catch (_: Exception) { false }

        fun replyActionFor(pkgs: Collection<String>, sender: String): Notification.Action? {
            val s = sender.lowercase()
            return replies.entries.firstOrNull { (k, _) ->
                val (pkg, who) = k.split("|", limit = 2)
                pkg in pkgs && (who == s || who.startsWith("$s ") || who.contains(s))
            }?.value
        }
    }
}
