package com.ultron.assistant

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class ChatMsg(val id: Long, val role: String, val text: String)   // role: you | ultron | note | tool | error
data class Confirm(val id: String, val tool: String, val detail: String)

/** Talks to the Ultron server on the PC: at home over Wi-Fi, away through Tailscale. */
class UltronClient(private val ctx: Context, private val prefs: Prefs) {
    enum class Conn { OFFLINE, CONNECTING, ONLINE }

    val conn = MutableStateFlow(Conn.OFFLINE)
    val where = MutableStateFlow("")                       // "at home" / "via Tailscale"
    val messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val busy = MutableStateFlow(false)
    val confirm = MutableStateFlow<Confirm?>(null)
    val lastError = MutableStateFlow("")

    private val ids = AtomicLong(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private var base: String? = null
    @Volatile private var started = false
    @Volatile private var gen = 0
    private var streamingId: Long? = null
    private var waiter: CompletableDeferred<String>? = null
    private var lastUltronText = ""

    // ------------------------------------------------------------------ connection
    fun start() {
        if (started) return
        started = true
        val myGen = ++gen
        scope.launch {
            var backoff = 1000L
            while (started && gen == myGen) {
                if (conn.value == Conn.OFFLINE) {
                    conn.value = Conn.CONNECTING
                    val url = pickServer()
                    if (url == null) {
                        conn.value = Conn.OFFLINE
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(30_000)
                        continue
                    }
                    backoff = 1000L
                    openSocket(url)
                }
                delay(1500)
            }
        }
    }

    fun restart() {
        started = false
        ws?.close(1000, null)
        ws = null
        conn.value = Conn.OFFLINE
        start()
    }

    private fun quick(timeoutMs: Long) = http.newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /** The first address that answers: home Wi-Fi first (fast), then Tailscale. */
    private fun pickServer(): String? {
        for ((url, label, t) in listOf(
            Triple(prefs.homeUrl, "at home", 2500L),
            Triple(prefs.awayUrl, "via Tailscale", 8000L),
        )) {
            if (url.isBlank()) continue
            val res = check(url, prefs.token, t)
            if (res == null) {
                where.value = label
                base = url
                return url
            }
            lastError.value = res
        }
        return null
    }

    /** null = OK, otherwise a reason. */
    fun check(url: String, token: String, timeoutMs: Long = 6000): String? = try {
        quick(timeoutMs).newCall(
            Request.Builder().url("$url/api/status").header("Authorization", "Bearer $token").build()
        ).execute().use { r ->
            when (r.code) {
                200 -> null
                401 -> "Wrong access key"
                else -> "The server answered ${r.code}"
            }
        }
    } catch (e: Exception) {
        "Can't reach $url (${e.javaClass.simpleName})"
    }

    private fun openSocket(url: String) {
        val wsUrl = url.replaceFirst("http", "ws") + "/ws?token=" + URLEncoder.encode(prefs.token, "UTF-8")
        ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                conn.value = Conn.ONLINE
                lastError.value = ""
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try { handle(JSONObject(text)) } catch (_: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped()
        })
    }

    private fun dropped() {
        ws = null
        conn.value = Conn.OFFLINE
        if (busy.value) {
            busy.value = false
            waiter?.complete("I lost the connection to the computer.")
        }
    }

    private fun send(obj: JSONObject): Boolean = ws?.send(obj.toString()) ?: false

    // ------------------------------------------------------------------ events from the server
    private fun handle(ev: JSONObject) {
        when (ev.optString("type")) {
            "ready" -> send(JSONObject().put("type", "hello")
                .put("device", "Android phone (${Build.MANUFACTURER} ${Build.MODEL})").put("local", false)
                .put("phone", true))
            "phone_action" -> {                                   // Ultron wants the phone to do something
                val id = ev.optString("id")
                scope.launch {
                    val result = Phone.run(ctx, ev)
                    send(JSONObject().put("type", "phone_result").put("id", id).put("result", result))
                }
            }
            "conversation" -> prefs.conversationId = ev.optString("id")
            "delta" -> appendStream(ev.optString("text"))
            "note" -> add("note", ev.optString("content"))
            "tool_start" -> add("tool", "⚙ " + ev.optString("name").replace('_', ' '))
            "reply" -> {
                val content = ev.optString("content")
                val sid = streamingId
                if (sid != null) {
                    messages.update { list -> list.map { if (it.id == sid) it.copy(text = content) else it } }
                } else add("ultron", content)
                streamingId = null
                lastUltronText = content
            }
            "confirm" -> {
                val c = Confirm(ev.optString("id"), ev.optString("tool"), ev.optString("detail"))
                confirm.value = c
                if (UltronApp.instance.visible == 0) notifyConfirm(c)
            }
            "confirm_expired" -> {
                confirm.value = null
                cancelConfirmNotification()
            }
            "error" -> {
                val m = ev.optString("message")
                add("error", m)
                lastUltronText = m
            }
            "done" -> {
                busy.value = false
                streamingId = null
                waiter?.complete(lastUltronText)
                waiter = null
            }
        }
    }

    fun add(role: String, text: String): Long {
        val id = ids.getAndIncrement()
        messages.update { (it + ChatMsg(id, role, text)).takeLast(300) }
        return id
    }

    private fun appendStream(piece: String) {
        val sid = streamingId
        if (sid == null) {
            streamingId = add("ultron", piece)
        } else {
            messages.update { list -> list.map { if (it.id == sid) it.copy(text = it.text + piece) else it } }
        }
    }

    // ------------------------------------------------------------------ actions
    /** Send a message; returns Ultron's final answer (for speaking it). */
    suspend fun ask(text: String, brain: String? = null): String {
        if (conn.value != Conn.ONLINE) {
            start()
            withTimeoutOrNull(12_000) { while (conn.value != Conn.ONLINE) delay(200) }
            if (conn.value != Conn.ONLINE) {
                add("you", text)
                add("error", "Can't reach Ultron on the computer. ${lastError.value}")
                return "I can't reach the computer right now."
            }
        }
        val w = CompletableDeferred<String>()
        waiter = w
        lastUltronText = ""
        add("you", text)
        busy.value = true
        val msg = JSONObject().put("type", "chat").put("text", text)
        prefs.conversationId?.let { msg.put("conversation_id", it) }
        brain?.let { msg.put("brain", it) }
        if (!send(msg)) {
            busy.value = false
            return "I can't reach the computer right now."
        }
        return withTimeoutOrNull(10 * 60_000) { w.await() } ?: ""
    }

    fun stop() { send(JSONObject().put("type", "stop")) }

    fun answer(id: String, approved: Boolean) {
        send(JSONObject().put("type", "confirm_reply").put("id", id).put("approved", approved))
        confirm.value = null
        cancelConfirmNotification()
    }

    fun newChat() {
        prefs.conversationId = null
        messages.value = emptyList()
    }

    /** Speech → text, done by the PC (free Groq Whisper / Gemini). */
    suspend fun transcribe(pcm: ByteArray): String = withContext(Dispatchers.IO) {
        val url = base ?: pickServer() ?: throw Exception("Can't reach the computer")
        val req = Request.Builder().url("$url/api/transcribe")
            .header("Authorization", "Bearer ${prefs.token}")
            .post(pcm.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newBuilder().readTimeout(40, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (r.code == 404) throw Exception("Update Ultron on the computer (needs version 1.16 or newer)")
            if (!r.isSuccessful) throw Exception(
                try { JSONObject(body).optString("detail") } catch (_: Exception) { "error ${r.code}" })
            JSONObject(body).optString("text")
        }
    }

    // ------------------------------------------------------------------ approvals while the app is closed
    private fun notifyConfirm(c: Confirm) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun action(approve: Boolean): PendingIntent = PendingIntent.getBroadcast(
            ctx, if (approve) 1 else 2,
            Intent(ctx, ConfirmReceiver::class.java).putExtra("id", c.id).putExtra("approve", approve),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, UltronApp.CH_ASK)
            .setSmallIcon(R.drawable.ic_stat_ultron)
            .setContentTitle("Ultron needs your OK")
            .setContentText(c.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.detail))
            .setContentIntent(open)
            .addAction(0, "Approve", action(true))
            .addAction(0, "Deny", action(false))
            .setAutoCancel(true)
            .build()
        try { nm.notify(CONFIRM_ID, n) } catch (_: SecurityException) {}
    }

    private fun cancelConfirmNotification() {
        ctx.getSystemService(NotificationManager::class.java).cancel(CONFIRM_ID)
    }

    companion object { const val CONFIRM_ID = 77 }
}
