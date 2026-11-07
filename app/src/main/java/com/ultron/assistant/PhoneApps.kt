package com.ultron.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

/** Apps, music, volume, alarms, torch and directions on the phone. Every function returns what to say. */
object PhoneApps {
    private const val NEED_OVERLAY = "I can't open apps while Ultron isn't on screen yet — in the Ultron app → " +
        "⚙ Settings → Phone powers, allow “Open apps from the background”."

    /** Android only lets an app open other apps from the background with one of these. */
    fun canOpenApps(ctx: Context) =
        UltronApp.instance.visible > 0 || Settings.canDrawOverlays(ctx) || AutoSend.enabled(ctx)

    private fun start(ctx: Context, i: Intent): String? {
        if (!canOpenApps(ctx)) return NEED_OVERLAY
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (e: Exception) {
            "That didn't open (${e.javaClass.simpleName})."
        }
    }

    private fun installed(ctx: Context, pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    // ------------------------------------------------------------------ apps
    private val ALIASES = mapOf(
        "camera" to "camera", "gallery" to "gallery", "photos" to "photos", "settings" to "settings",
        "phone" to "phone", "dialer" to "phone", "contacts" to "contacts", "messages" to "messages",
        "sms" to "messages", "insta" to "instagram", "yt" to "youtube", "fb" to "facebook", "maps" to "maps",
        "calculator" to "calculator", "clock" to "clock", "play store" to "play store", "playstore" to "play store",
        "gpay" to "google pay", "g pay" to "google pay", "paytm" to "paytm", "phonepe" to "phonepe", "phone pe" to "phonepe",
        "files" to "files", "file manager" to "files", "chrome" to "chrome", "browser" to "chrome",
    )

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1.0 - dp[b.length].toDouble() / maxOf(a.length, b.length).coerceAtLeast(1)
    }

    fun openApp(ctx: Context, name: String): String {
        var q = name.lowercase().replace(Regex("\\b(app|application|the|my)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        q = ALIASES[q] ?: q
        if (q == "camera") {
            start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))?.let { return it }
            return "Opened the camera."
        }
        if (q == "settings") {
            start(ctx, Intent(Settings.ACTION_SETTINGS))?.let { return it }
            return "Opened Settings."
        }
        val pm = ctx.packageManager
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        var best: Pair<String, String>? = null                 // label, package
        var bestScore = 0.0
        for (ri in launchers) {
            val label = ri.loadLabel(pm).toString()
            val l = label.lowercase()
            val score = when {
                l == q -> 1.0
                l.startsWith(q) || l.split(" ").firstOrNull() == q -> 0.92
                l.contains(q) -> 0.85
                else -> maxOf(similarity(l, q), l.split(" ").maxOf { similarity(it, q) } * 0.9)
            }
            if (score > bestScore) { bestScore = score; best = label to ri.activityInfo.packageName }
        }
        val (label, pkg) = best?.takeIf { bestScore >= 0.7 } ?: return "I couldn't find an app called “$name” on the phone."
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return "$label can't be opened directly."
        start(ctx, intent)?.let { return it }
        return "Opened $label."
    }

    fun openUrl(ctx: Context, url: String): String {
        val u = if (url.startsWith("http")) url else "https://$url"
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))?.let { return it }
        return "Opened it on the phone."
    }

    // ------------------------------------------------------------------ music
    fun playMusic(ctx: Context, query: String, app: String): String {
        val q = query.trim()
        if (q.isBlank()) return media(ctx, "play")
        val spotify = installed(ctx, "com.spotify.music")
        val ytMusic = installed(ctx, "com.google.android.apps.youtube.music")
        val pkg = when (app) {
            "youtube" -> null
            "youtube_music" -> if (ytMusic) "com.google.android.apps.youtube.music" else null
            else -> if (spotify) "com.spotify.music" else if (ytMusic) "com.google.android.apps.youtube.music" else null
        }
        if (pkg != null) {
            // the same "play X" request Google Assistant uses — the app finds it and starts playing
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(pkg)
                .putExtra(SearchManager.QUERY, q)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            start(ctx, i)?.let { return it }
            val appName = if (pkg == "com.spotify.music") "Spotify" else "YouTube Music"
            return "Playing $q on $appName."
        }
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, q)
        if (installed(ctx, "com.google.android.youtube")) {
            start(ctx, yt)?.let { return it }
            return "Searching YouTube for $q."
        }
        return openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))
    }

    fun media(ctx: Context, what: String): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val (code, said) = when (what.lowercase()) {
            "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE to "Paused."
            "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY to "Playing."
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT to "Next one."
            "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Going back."
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Done."
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return said
    }

    fun volume(ctx: Context, what: String): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val s = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(s)
        return try {
            when (val w = what.lowercase().trim()) {
                "up" -> { repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, 0) }; "Volume up." }
                "down" -> { repeat(2) { am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, 0) }; "Volume down." }
                "mute" -> { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0); "Muted." }
                "unmute" -> { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0); "Unmuted." }
                else -> {
                    val pct = w.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: return "Say a volume like 50."
                    am.setStreamVolume(s, (max * pct + 50) / 100, 0)
                    "Volume $pct%."
                }
            }
        } catch (_: SecurityException) {
            "The phone won't let me change the volume while Do Not Disturb is on."
        }
    }

    // ------------------------------------------------------------------ alarms, timers, torch, maps
    fun alarm(ctx: Context, hhmm: String, label: String = ""): String {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(hhmm) ?: return "Tell me the alarm time, like 6:30 am."
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        start(ctx, i)?.let { return it }
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "Alarm set for $h12:${"%02d".format(min)} $ampm."
    }

    fun timer(ctx: Context, seconds: Int): String {
        if (seconds <= 0) return "How long should the timer be?"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        start(ctx, i)?.let { return it }
        val text = when {
            seconds % 3600 == 0 -> "${seconds / 3600} hour${if (seconds >= 7200) "s" else ""}"
            seconds % 60 == 0 -> "${seconds / 60} minute${if (seconds >= 120) "s" else ""}"
            else -> "$seconds seconds"
        }
        return "Timer set for $text."
    }

    fun flashlight(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        return try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This phone has no flashlight."
            cm.setTorchMode(id, on)
            if (on) "Torch on." else "Torch off."
        } catch (e: Exception) { "I couldn't switch the torch: ${e.message}" }
    }

    fun navigate(ctx: Context, place: String): String {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
        if (installed(ctx, "com.google.android.apps.maps")) i.setPackage("com.google.android.apps.maps")
        else i.data = Uri.parse("geo:0,0?q=" + Uri.encode(place))
        start(ctx, i)?.let { return it }
        return "Starting directions to $place."
    }
}
