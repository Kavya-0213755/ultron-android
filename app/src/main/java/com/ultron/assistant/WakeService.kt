package com.ultron.assistant

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Listens for "Hey Ultron" in the background, entirely on the phone (offline Vosk model, nothing is sent
 * anywhere until the wake word is heard). Then records the request and hands it to VoiceTurn.
 */
class WakeService : Service() {
    @Volatile private var running = false
    @Volatile private var talkNow = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                UltronApp.instance.prefs.wake = false
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TALK -> talkNow = true
        }
        try {
            goForeground("Say “Hey Ultron”")
        } catch (e: Exception) {                 // Android refuses a background mic start (e.g. after a reboot)
            status.value = "Open the Ultron app once to start listening"
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker == null) {
            running = true
            worker = Thread({ listen() }, "ultron-wake").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker = null
        active.value = false
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getService(
            this, 1, Intent(this, WakeService::class.java).setAction(ACTION_TALK), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, WakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, UltronApp.CH_LISTEN)
            .setSmallIcon(R.drawable.ic_stat_ultron)
            .setContentTitle("Ultron")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Talk now", talk)
            .addAction(0, "Stop listening", stop)
            .build()
    }

    private fun goForeground(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTE_ID, n)
    }

    private fun show(text: String) {
        status.value = text
        try {
            getSystemService(NotificationManager::class.java).notify(NOTE_ID, notification(text))
        } catch (_: SecurityException) {}
    }

    private fun listen() {
        active.value = true
        var rec: AudioRecord? = null
        var model: Model? = null
        var recog: Recognizer? = null
        try {
            if (!VoskModel.ready(this)) throw Exception("the wake-word model isn't downloaded yet")
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(VoskModel.dir(this).absolutePath)
            recog = Recognizer(model, 16000f, GRAMMAR)
            rec = Recorder.open()
            rec.startRecording()
            show("Say “Hey Ultron”")
            val buf = ByteArray(3200)                          // 0.1 s
            var announced: String? = null
            while (running) {
                if (CallState.inCall.value) {                  // on a call: give the mic to the call
                    show("Paused during the call")
                    rec.stop()
                    while (running && CallState.inCall.value) Thread.sleep(500)
                    rec.startRecording()
                    recog.reset()
                    show("Say “Hey Ultron”")
                    continue
                }
                val caller = CallState.ringing.value
                if (caller == null) announced = null
                else if (caller != announced && UltronApp.instance.prefs.announceCalls) {
                    announced = caller
                    incomingCall(rec, model, caller)
                    recog.reset()
                    continue
                }
                if (talkNow) {
                    talkNow = false
                    converse(rec)
                    recog.reset()
                    continue
                }
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val json = if (recog.acceptWaveForm(buf, n)) recog.result else recog.partialResult
                if (isWake(json)) {
                    recog.reset()
                    converse(rec)
                    recog.reset()
                }
            }
        } catch (e: Exception) {
            show("Stopped: ${e.message}")
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            try { recog?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: Exception) {}
            active.value = false
        }
    }

    /** "Call from Papa. Answer or reject?" — then listens for the answer (offline, quick). */
    private fun incomingCall(rec: AudioRecord, model: Model, caller: String) {
        val app = UltronApp.instance
        show("Call from $caller — say “answer” or “reject”")
        runBlocking { app.speaker.say("Call from $caller. Answer or reject?") }
        rec.stop()
        rec.startRecording()                                   // drop our own voice
        val r = Recognizer(model, 16000f, CALL_GRAMMAR)
        val buf = ByteArray(3200)
        val until = System.currentTimeMillis() + 15_000
        try {
            while (running && CallState.ringing.value != null && System.currentTimeMillis() < until) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (!r.acceptWaveForm(buf, n)) continue                 // only whole, finished words count
                val t = try { JSONObject(r.result).optString("text") } catch (_: Exception) { "" }
                when {
                    Regex("\\b(answer|pick|receive|accept)\\b").containsMatchIn(t) -> {
                        val said = Phone.answerCall(this)
                        show(said)
                        return
                    }
                    Regex("\\b(reject|decline|cut|busy|nahi)\\b").containsMatchIn(t) -> {
                        val ok = Phone.endCall(this)
                        show(if (ok) "Call declined" else "Couldn't decline the call")
                        if (ok && Regex("\\bbusy\\b").containsMatchIn(t)) runBlocking { app.speaker.say("Declined.") }
                        return
                    }
                }
            }
        } finally {
            r.close()
            show("Say “Hey Ultron”")
        }
    }

    private fun converse(rec: AudioRecord) = runBlocking {
        VoiceTurn.beepStart()
        show("Listening…")
        var said = VoiceTurn.run(rec, waitMs = 6000, flush = false) { !running }
        val prefs = UltronApp.instance.prefs
        while (said && running && prefs.followUp) {             // keep talking without the wake word
            show("Listening for a follow-up…")
            said = VoiceTurn.run(rec, waitMs = 5000, flush = true) { !running }
        }
        rec.stop()
        rec.startRecording()                                    // drop what was recorded meanwhile
        show("Say “Hey Ultron”")
    }

    companion object {
        const val ACTION_TALK = "com.ultron.assistant.TALK"
        const val ACTION_STOP = "com.ultron.assistant.STOP"
        private const val NOTE_ID = 11

        val active = MutableStateFlow(false)
        val status = MutableStateFlow("")

        private val GRAMMAR = listOf(
            "hey ultron", "ultron", "ok ultron", "hey ultra", "hey alton", "hey elton", "hey electron",
            "altron", "hey altron", "[unk]"
        ).joinToString(",", "[", "]") { "\"$it\"" }

        private val CALL_GRAMMAR = listOf(
            "answer", "answer it", "pick up", "receive", "accept", "reject", "decline", "cut it", "busy",
            "nahi", "[unk]"
        ).joinToString(",", "[", "]") { "\"$it\"" }

        private val NAMES = Regex("\\b(ultron|altron)\\b")
        private val HEY_NAMES = Regex("\\b(hey|ok) (ultra|alton|elton|electron)\\b")

        fun isWake(json: String): Boolean {
            val o = try { JSONObject(json) } catch (_: Exception) { return false }
            val t = (o.optString("text").ifEmpty { o.optString("partial") }).lowercase()
            return NAMES.containsMatchIn(t) || HEY_NAMES.containsMatchIn(t)
        }

        fun start(ctx: Context, talk: Boolean = false) {
            val i = Intent(ctx, WakeService::class.java)
            if (talk) i.action = ACTION_TALK
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WakeService::class.java).setAction(ACTION_STOP))
        }
    }
}
