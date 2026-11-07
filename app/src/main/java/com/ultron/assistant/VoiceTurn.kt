package com.ultron.assistant

import android.media.AudioManager
import android.media.AudioRecord
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** One spoken exchange: listen → the PC turns speech into text → Ultron answers → the phone speaks it. */
object VoiceTurn {
    val state = MutableStateFlow("")          // "" | listening | thinking | speaking
    val level = MutableStateFlow(0f)
    val heard = MutableStateFlow("")

    private val tones by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }
    fun beepStart() = try { tones.startTone(ToneGenerator.TONE_PROP_BEEP, 120); Unit } catch (_: Exception) {}
    fun beepDone() = try { tones.startTone(ToneGenerator.TONE_PROP_ACK, 120); Unit } catch (_: Exception) {}

    /**
     * Uses an AudioRecord that is already recording. [flush] drops audio buffered while the phone was
     * talking. Returns true if the owner said something.
     */
    suspend fun run(rec: AudioRecord, waitMs: Int, flush: Boolean, cancelled: () -> Boolean): Boolean {
        val app = UltronApp.instance
        state.value = "listening"
        heard.value = ""
        app.speaker.holdAudio()
        try {
            val pcm = withContext(Dispatchers.IO) {
                if (flush) { rec.stop(); rec.startRecording() }
                Recorder.captureUtterance(rec, waitMs = waitMs, cancelled = cancelled, onLevel = { level.value = it })
            }
            level.value = 0f
            if (pcm.isEmpty() || cancelled()) return false
            beepDone()
            state.value = "thinking"
            val text = try {
                app.client.transcribe(pcm)
            } catch (e: Exception) {
                state.value = "speaking"
                app.speaker.say("Sorry, I couldn't understand. ${e.message ?: ""}")
                return false
            }
            if (text.isBlank()) return false
            heard.value = text
            val reply = app.client.ask(text)
            if (app.prefs.speak && reply.isNotBlank() && !cancelled()) {
                state.value = "speaking"
                app.speaker.say(reply)
            }
            return true
        } finally {
            level.value = 0f
            state.value = ""
            app.speaker.releaseAudio()
        }
    }
}
