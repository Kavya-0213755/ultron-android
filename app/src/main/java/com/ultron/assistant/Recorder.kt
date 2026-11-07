package com.ultron.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/** Microphone at 16 kHz mono 16-bit, plus "record until they stop talking". */
object Recorder {
    const val RATE = 16000
    const val CHUNK = 960                         // 30 ms of audio in bytes

    @SuppressLint("MissingPermission")
    fun open(): AudioRecord {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(min, RATE * 2)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("The microphone is busy (another app may be using it)")
        }
        return rec
    }

    fun rms(buf: ByteArray, n: Int): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        return if (n < 2) 0.0 else sqrt(sum / (n / 2))
    }

    /**
     * Read from a started AudioRecord until the person stops talking.
     * Returns the speech (empty if nobody spoke within [waitMs]).
     */
    fun captureUtterance(
        rec: AudioRecord,
        waitMs: Int = 6000,
        silenceMs: Int = 1100,
        maxMs: Int = 15000,
        cancelled: () -> Boolean = { false },
        onLevel: (Float) -> Unit = {},
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val pre = ArrayDeque<ByteArray>()            // 0.3 s before the speech starts
        val buf = ByteArray(CHUNK)
        var floor = 0.0
        var calib = 0
        var speaking = false
        var quietMs = 0
        var elapsed = 0
        var spokenMs = 0
        while (!cancelled()) {
            val n = rec.read(buf, 0, CHUNK)
            if (n <= 0) continue
            elapsed += 30
            val level = rms(buf, n)
            onLevel((level / 3000.0).coerceIn(0.0, 1.0).toFloat())
            if (calib < 8) {                           // first quarter second: learn the room's noise
                floor = if (calib == 0) level else minOf(floor, level)   // quietest moment = the room
                calib++
            }
            val loud = level > max(floor * 2.2, 350.0)
            if (!speaking) {
                pre.addLast(buf.copyOf(n))
                if (pre.size > 10) pre.removeFirst()
                if (loud) {
                    speaking = true
                    pre.forEach { out.write(it) }
                    pre.clear()
                } else {
                    floor = floor * 0.95 + level * 0.05
                    if (elapsed > waitMs) return ByteArray(0)
                }
                continue
            }
            out.write(buf, 0, n)
            spokenMs += 30
            quietMs = if (loud) 0 else quietMs + 30
            if ((quietMs >= silenceMs && spokenMs > 400) || spokenMs >= maxMs) break
        }
        return out.toByteArray()
    }
}
