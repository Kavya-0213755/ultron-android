package com.ultron.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** The small offline speech model used only to notice "Hey Ultron" (36 MB, downloaded once, free). */
object VoskModel {
    private const val NAME = "vosk-model-small-en-in-0.4"
    private const val URL = "https://alphacephei.com/vosk/models/$NAME.zip"

    private fun root(ctx: Context) = File(ctx.filesDir, "vosk")
    fun dir(ctx: Context) = File(root(ctx), NAME)
    fun ready(ctx: Context) = File(root(ctx), ".ok-$NAME").exists()

    suspend fun download(ctx: Context, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val root = root(ctx)
        root.deleteRecursively()
        root.mkdirs()
        val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("download failed (${r.code})")
            val body = r.body ?: throw Exception("empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: 36_000_000L
            var read = 0L
            val counting = object : FilterInputStream(body.byteStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0) {
                        read += n
                        progress((read.toFloat() / total).coerceAtMost(1f))
                    }
                    return n
                }
            }
            unzip(counting, root)
        }
        File(root, ".ok-$NAME").writeText("ok")
    }

    private fun unzip(input: InputStream, into: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val f = File(into, e.name)
                if (!f.canonicalPath.startsWith(into.canonicalPath)) continue
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }
}
