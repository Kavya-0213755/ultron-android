package com.ultron.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The phone's own powers: calls, SMS, WhatsApp, reading messages. Every function returns what to say. */
object Phone {
    val CALL_PERMS = arrayOf(
        Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
    )

    fun has(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun need(perm: String, what: String) =
        "I need permission for $what first — open the Ultron app → ⚙ Settings → “Allow calls, messages & contacts”."

    private fun whoNotFound(name: String) = "I couldn't find “$name” in your contacts."

    // ------------------------------------------------------------------ calls
    @SuppressLint("MissingPermission")
    fun call(ctx: Context, to: String): String {
        if (!has(ctx, Manifest.permission.CALL_PHONE)) return need(Manifest.permission.CALL_PHONE, "phone calls")
        if (!Contacts.allowed(ctx) && to.none { it.isDigit() }) return need(Manifest.permission.READ_CONTACTS, "contacts")
        val p = Contacts.find(ctx, to) ?: return whoNotFound(to)
        val tm = ctx.getSystemService(TelecomManager::class.java)
        return try {
            tm.placeCall(Uri.fromParts("tel", p.number, null), Bundle())
            "Calling ${p.name}."
        } catch (e: Exception) {
            "I couldn't start the call: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun answerCall(ctx: Context, speaker: Boolean = UltronApp.instance.prefs.speakerOnAnswer): String {
        if (!has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) return need(Manifest.permission.ANSWER_PHONE_CALLS, "answering calls")
        val tm = ctx.getSystemService(TelecomManager::class.java)
        return try {
            tm.acceptRingingCall()
            if (speaker) {
                Thread {
                    Thread.sleep(1200)
                    speakerOn(ctx)
                }.start()
            }
            "Answered" + if (speaker) " on speaker." else "."
        } catch (e: Exception) { "I couldn't answer: ${e.message}" }
    }

    @SuppressLint("MissingPermission")
    fun endCall(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28 || !has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        return try { ctx.getSystemService(TelecomManager::class.java).endCall() } catch (_: Exception) { false }
    }

    @Suppress("DEPRECATION")
    fun speakerOn(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { am.setCommunicationDevice(it) }
            } else {
                am.mode = AudioManager.MODE_IN_CALL
                am.isSpeakerphoneOn = true
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun missedCalls(ctx: Context): String {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return need(Manifest.permission.READ_CALL_LOG, "the call log")
        val since = System.currentTimeMillis() - 24 * 3600_000L
        val items = mutableListOf<String>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString(), since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && items.size < 10) {
                val who = c.getString(0)?.takeIf { it.isNotBlank() } ?: c.getString(1) ?: "unknown"
                items += "$who at ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(c.getLong(2)))}"
            }
        }
        return if (items.isEmpty()) "No missed calls in the last 24 hours."
        else "${items.size} missed call${if (items.size > 1) "s" else ""}: ${items.joinToString("; ")}."
    }

    // ------------------------------------------------------------------ messages
    fun sendSms(ctx: Context, number: String, text: String): Boolean {
        if (!has(ctx, Manifest.permission.SEND_SMS)) return false
        return try {
            val sms = Dnd.smsManager(ctx)
            val parts = sms.divideMessage(text)
            if (parts.size > 1) sms.sendMultipartTextMessage(number, null, parts, null, null)
            else sms.sendTextMessage(number, null, text, null, null)
            true
        } catch (_: Exception) { false }
    }

    private fun installed(ctx: Context, pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    fun sendMessage(ctx: Context, to: String, text: String, app: String): String {
        val wa = listOf("com.whatsapp", "com.whatsapp.w4b").filter { installed(ctx, it) }
        // 1) a chat that just messaged us: reply right from its notification (no screen needed)
        if (app != "sms" && wa.isNotEmpty()) {
            NotifListener.replyActionFor(wa, to)?.let { action ->
                if (NotifListener.reply(ctx, action, text)) return "Sent on WhatsApp to $to: “$text”."
            }
        }
        if (!Contacts.allowed(ctx) && to.none { it.isDigit() }) return need(Manifest.permission.READ_CONTACTS, "contacts")
        val p = Contacts.find(ctx, to) ?: return whoNotFound(to)
        // 2) WhatsApp hands-free: open the chat with the text, the accessibility helper presses Send
        if (app != "sms" && wa.isNotEmpty() && AutoSend.enabled(ctx)) {
            val ok = AutoSend.send(ctx, wa.first(), Contacts.international(p.number), text)
            if (ok) return "Sent on WhatsApp to ${p.name}: “$text”."
            if (app == "whatsapp") return "I opened WhatsApp for ${p.name} but couldn't press Send — please check the phone."
        }
        if (app == "whatsapp") {
            if (wa.isEmpty()) return "WhatsApp isn't installed on this phone."
            return "To send WhatsApp messages hands-free, turn on Ultron in the phone's Accessibility settings " +
                "(Ultron app → ⚙ Settings → “Send WhatsApp hands-free”). I can send it as an SMS instead."
        }
        // 3) SMS
        if (!has(ctx, Manifest.permission.SEND_SMS)) return need(Manifest.permission.SEND_SMS, "sending SMS")
        return if (sendSms(ctx, p.number, text)) "Sent an SMS to ${p.name}: “$text”." else "The SMS to ${p.name} failed."
    }

    @SuppressLint("MissingPermission")
    private fun unreadSms(ctx: Context): List<InMsg> {
        if (!has(ctx, Manifest.permission.READ_SMS)) return emptyList()
        val out = mutableListOf<InMsg>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.READ} = 0", null, "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 15) {
                    val addr = c.getString(0) ?: ""
                    out += InMsg("SMS", "sms", Contacts.nameFor(ctx, addr) ?: addr, c.getString(1) ?: "", c.getLong(2), false)
                }
            }
        } catch (_: Exception) {}
        return out.reversed()
    }

    fun readMessages(ctx: Context, from: String, count: Int): String {
        var msgs = NotifListener.unread(ctx)
        val viaListener = NotifListener.enabled(ctx)
        if (!viaListener || msgs.isEmpty()) {
            val sms = unreadSms(ctx)
            msgs = (msgs + sms).distinctBy { it.sender + it.text }.sortedBy { it.time }
        }
        var label = "new"
        if (msgs.isEmpty() && viaListener) {
            msgs = NotifListener.recent(30)
            label = "recent"
        }
        if (from.isNotBlank()) {
            val f = from.lowercase()
            msgs = msgs.filter { it.sender.lowercase().contains(f) || f.contains(it.sender.lowercase()) }
        }
        msgs = msgs.filter { it.text.isNotBlank() }.takeLast(count.coerceIn(1, 25))
        if (msgs.isEmpty()) {
            return when {
                !viaListener && !has(ctx, Manifest.permission.READ_SMS) ->
                    "I can't see your messages yet — in the Ultron app → ⚙ Settings turn on “Read notifications”."
                from.isNotBlank() -> "No new messages from $from."
                else -> "No new messages."
            }
        }
        val fmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val lines = msgs.map { "${it.app} from ${it.sender} at ${fmt.format(Date(it.time))}: ${it.text.take(300)}" }
        return "${msgs.size} $label message${if (msgs.size > 1) "s" else ""}:\n" + lines.joinToString("\n")
    }

    // ------------------------------------------------------------------ requests from Ultron on the PC
    fun run(ctx: Context, ev: JSONObject): String {
        val to = ev.optString("to")
        val text = ev.optString("text")
        return try {
            when (ev.optString("action")) {
                "call" -> call(ctx, to)
                "send_message" -> sendMessage(ctx, to, text, ev.optString("app", "auto"))
                "read_messages" -> readMessages(ctx, to, ev.optInt("count", 10))
                "answer_call" -> answerCall(ctx)
                "reject_call", "end_call" ->
                    if (endCall(ctx)) (if (ev.optString("action") == "end_call") "Call ended." else "Call declined.")
                    else "There's no call to end (or I'm not allowed to manage calls)."
                "missed_calls" -> missedCalls(ctx)
                "dnd_on" -> Dnd.start(ctx, text)
                "dnd_off" -> Dnd.stop(ctx)
                "find_contact" -> Contacts.find(ctx, to)?.let { "${it.name}: ${it.number}" } ?: whoNotFound(to)
                else -> "The phone app doesn't know “${ev.optString("action")}” yet — update the Ultron app."
            }
        } catch (e: SecurityException) {
            "The phone refused: a permission is missing (${e.message})."
        } catch (e: Exception) {
            "Something went wrong on the phone: ${e.message}"
        }
    }

    fun openAppSettings(ctx: Context) {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
