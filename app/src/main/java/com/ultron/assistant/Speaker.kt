package com.ultron.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Speaks Ultron's answers with the phone's own (free, offline) voice. Music pauses while it talks. */
class Speaker(ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private var ready = false
    private var done: CompletableDeferred<Unit>? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        ).build()

    private lateinit var tts: TextToSpeech

    private fun onReady(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val india = Locale("en", "IN")
            if (tts.isLanguageAvailable(india) >= TextToSpeech.LANG_AVAILABLE) tts.language = india
            tts.setSpeechRate(1.05f)
        }
    }

    init {
        tts = TextToSpeech(ctx.applicationContext) { status -> onReady(status) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done?.complete(Unit) }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { done?.complete(Unit) }
            override fun onError(id: String?, code: Int) { done?.complete(Unit) }
        })
    }

    /** Hold other audio (music pauses) while listening and answering. */
    fun holdAudio() { am.requestAudioFocus(focus) }
    fun releaseAudio() { am.abandonAudioFocusRequest(focus) }

    suspend fun say(text: String) {
        val clean = forSpeech(text)
        if (!ready || clean.isBlank()) return
        val d = CompletableDeferred<Unit>()
        done = d
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
        withTimeoutOrNull(60_000) { d.await() }
    }

    fun stop() {
        tts.stop()
        done?.complete(Unit)
    }

    companion object {
        /** Light clean-up of Markdown for the chat bubbles. */
        fun forDisplay(md: String): String {
            var t = md
            t = Regex("!?\\[([^\\]]*)\\]\\(([^)]*)\\)").replace(t) { it.groupValues[1] }
            t = t.replace("**", "").replace("`", "")
            t = Regex("(?m)^#{1,6}\\s*").replace(t, "")
            return t.trim()
        }

        fun forSpeech(md: String): String {
            var t = md
            t = Regex("```[\\s\\S]*?```").replace(t, " ")
            t = Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)").replace(t) { it.groupValues[1] }
            t = Regex("https?://\\S+").replace(t, "")
            t = Regex("[*_#`>|]").replace(t, "")
            t = Regex("\\n+").replace(t, ". ")
            t = Regex("\\s+").replace(t, " ").trim()
            if (t.length > 600) t = t.take(600).substringBeforeLast(". ", t.take(600)) + "."
            return t
        }
    }
}
