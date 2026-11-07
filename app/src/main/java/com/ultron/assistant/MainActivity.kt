package com.ultron.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultron.assistant.ui.Aurora
import com.ultron.assistant.ui.Chip
import com.ultron.assistant.ui.Eyebrow
import com.ultron.assistant.ui.Glass
import com.ultron.assistant.ui.GlassIconButton
import com.ultron.assistant.ui.GoldButton
import com.ultron.assistant.ui.HSpace
import com.ultron.assistant.ui.MiniEq
import com.ultron.assistant.ui.PulseDot
import com.ultron.assistant.ui.Reveal
import com.ultron.assistant.ui.TypingDots
import com.ultron.assistant.ui.U
import com.ultron.assistant.ui.UltronOrb
import com.ultron.assistant.ui.UltronTheme
import com.ultron.assistant.ui.VSpace
import com.ultron.assistant.ui.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val app get() = UltronApp.instance
    private val talkRequest = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { UltronTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val a = i?.action ?: return
        if (a == Intent.ACTION_ASSIST || a == Intent.ACTION_VOICE_COMMAND || a == WakeService.ACTION_TALK) {
            talkRequest.value += 1
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.prefs.token.isNotBlank()) app.client.start()
        if (app.prefs.wake && !WakeService.active.value && hasMic() && VoskModel.ready(this)) {
            WakeService.start(this)                       // (re)start listening whenever the app is opened
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf(if (app.prefs.token.isBlank()) "setup" else "chat") }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.02f))
            },
            label = "screen",
        ) { s ->
            when (s) {
                "setup" -> SetupScreen(first = app.prefs.token.isBlank()) { screen = "chat" }
                else -> ChatScreen(onSettings = { screen = "setup" })
            }
        }
    }

    // ------------------------------------------------------------------ shared bits
    @Composable
    private fun Field(value: String, change: (String) -> Unit, label: String, secret: Boolean = false) {
        OutlinedTextField(
            value, change, label = { Text(label) }, singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else
                androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = U.Gold, unfocusedBorderColor = U.Hairline, focusedLabelColor = U.Gold,
                unfocusedLabelColor = U.Muted, cursorColor = U.Gold, focusedTextColor = U.Ivory,
                unfocusedTextColor = U.Ivory, focusedContainerColor = U.Bg2.copy(alpha = 0.6f),
                unfocusedContainerColor = U.Bg2.copy(alpha = 0.4f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Toggle(label: String, sub: String? = null, value: Boolean, change: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, color = U.Ivory, style = MaterialTheme.typography.titleMedium)
                if (sub != null) Text(sub, color = U.Muted, style = MaterialTheme.typography.bodySmall)
            }
            HSpace(12.dp)
            Switch(
                checked = value, onCheckedChange = change,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = U.OnGold, checkedTrackColor = U.Gold, checkedBorderColor = U.Gold,
                    uncheckedThumbColor = U.Muted, uncheckedTrackColor = U.Glass, uncheckedBorderColor = U.Hairline,
                ),
            )
        }
    }

    @Composable
    private fun GhostButton(text: String, onClick: () -> Unit) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, U.Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, color = U.GoldLight, style = MaterialTheme.typography.labelLarge) }
    }

    // ------------------------------------------------------------------ setup / settings
    @Composable
    private fun SetupScreen(first: Boolean, done: () -> Unit) {
        val prefs = app.prefs
        var home by remember { mutableStateOf(prefs.homeUrl) }
        var away by remember { mutableStateOf(prefs.awayUrl) }
        var token by remember { mutableStateOf(prefs.token) }
        var speak by remember { mutableStateOf(prefs.speak) }
        var follow by remember { mutableStateOf(prefs.followUp) }
        var msg by remember { mutableStateOf("") }
        var testing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun save() {
            testing = true
            msg = "Checking…"
            scope.launch {
                val h = Prefs.clean(home)
                val a = Prefs.clean(away)
                val res = withContext(Dispatchers.IO) {
                    val r1 = app.client.check(h, token.trim(), 3000)
                    if (r1 == null) null else if (a.isBlank()) r1
                    else app.client.check(a, token.trim(), 8000)?.let { "$r1\n$it" }
                }
                testing = false
                if (res != null && res.contains("Wrong access key")) {
                    msg = "That access key isn't right."
                    return@launch
                }
                prefs.homeUrl = h; prefs.awayUrl = a; prefs.token = token
                prefs.speak = speak; prefs.followUp = follow
                app.client.restart()
                if (res == null) done() else msg = "Saved — but the computer didn't answer right now:\n$res"
            }
        }

        Aurora {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (first) {
                    VSpace(24.dp)
                    Reveal(0, Modifier.align(Alignment.CenterHorizontally)) { UltronOrb(size = 180.dp) }
                    Reveal(1) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Eyebrow("Ultron")
                            VSpace(6.dp)
                            Text("Your right hand.", style = MaterialTheme.typography.displaySmall, color = U.Ivory)
                            VSpace(6.dp)
                            Text("Connect to Ultron on your computer — at home over Wi-Fi, anywhere through Tailscale.",
                                color = U.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(onClick = done) { Icon(Icons.Filled.ArrowBack, "Back", tint = U.Ivory) }
                        HSpace(14.dp)
                        Column {
                            Eyebrow("Ultron")
                            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = U.Ivory)
                        }
                    }
                }

                Reveal(2) {
                    Glass {
                        Text("Connection", style = MaterialTheme.typography.titleMedium, color = U.Ivory)
                        VSpace(10.dp)
                        Field(home, { home = it }, "At home (Wi-Fi address)")
                        VSpace(10.dp)
                        Field(away, { away = it }, "Away (Tailscale address, optional)")
                        VSpace(10.dp)
                        Field(token, { token = it }, "Access key", secret = true)
                        VSpace(8.dp)
                        Text("On the computer:  grep auth_token ~/.config/ultron/config.yaml",
                            color = U.Faint, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Reveal(3) {
                    GoldButton(if (testing) "Checking…" else if (first) "Connect" else "Save",
                        Modifier.fillMaxWidth(), enabled = !testing && token.isNotBlank()) { save() }
                }
                AnimatedVisibility(msg.isNotBlank() && msg != "Checking…") {
                    Column {
                        Text(msg, color = U.Coral, style = MaterialTheme.typography.bodyMedium)
                        if (!first && msg.startsWith("Saved")) TextButton(onClick = done) {
                            Text("Continue anyway", color = U.GoldLight)
                        }
                    }
                }

                if (!first) {
                    Reveal(4) {
                        Glass {
                            Text("Voice", style = MaterialTheme.typography.titleMedium, color = U.Ivory)
                            Toggle("Speak answers aloud", value = speak) { speak = it; prefs.speak = it }
                            Toggle("Follow-up listening", "Keep listening a few seconds after an answer", follow) {
                                follow = it; prefs.followUp = it
                            }
                            VSpace(6.dp)
                            GhostButton("Let Ultron run in the background (battery)") { askBatteryExemption() }
                        }
                    }
                    Reveal(5) { Glass { PhonePowers() } }
                    VSpace(24.dp)
                }
            }
        }
    }

    @Composable
    private fun PhonePowers() {
        val prefs = app.prefs
        var tick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            tick++
            onPauseOrDispose { }
        }
        val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
        val nm = getSystemService(NotificationManager::class.java)
        val fresh = tick >= 0                                 // re-checked every time the screen comes back
        val callsOk = fresh && Phone.CALL_PERMS.all { Phone.has(this, it) }
        val listenOk = fresh && NotifListener.enabled(this)
        val autoOk = fresh && AutoSend.enabled(this)
        val policyOk = fresh && nm.isNotificationPolicyAccessGranted
        val overlayOk = fresh && Settings.canDrawOverlays(this)
        var reply by remember { mutableStateOf(prefs.dndReply) }
        var announceCalls by remember { mutableStateOf(prefs.announceCalls) }
        var speaker by remember { mutableStateOf(prefs.speakerOnAnswer) }
        var announceMsgs by remember { mutableStateOf(prefs.announceMessages) }
        var starred by remember { mutableStateOf(prefs.dndStarredRing) }

        Text("Phone powers", style = MaterialTheme.typography.titleMedium, color = U.Ivory)
        VSpace(4.dp)
        Text("“Open Instagram”, “play Kesariya”, “set an alarm for 6:30”, “torch on”, “call Papa”, “send a WhatsApp to " +
            "Rahul saying I'm on my way”, “read my messages”, “go to DND and say I'm driving”, “I'm back”. " +
            "Add “on the computer” to do it on the PC.", color = U.Muted, style = MaterialTheme.typography.bodySmall)
        VSpace(10.dp)
        PermRow("Calls, SMS and contacts", callsOk) { askPerms.launch(Phone.CALL_PERMS) }
        PermRow("Open apps & play music from the background", overlayOk) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        PermRow("Read & reply to messages", listenOk) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermRow("Send WhatsApp hands-free", autoOk) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PermRow("DND can silence the phone", policyOk) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        VSpace(6.dp)
        Text("If Android says “Restricted setting”: App info → ⋮ (top right) → “Allow restricted settings”, then try again.",
            color = U.Faint, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { Phone.openAppSettings(this@MainActivity) }) { Text("Open App info", color = U.GoldLight) }
        Toggle("Announce incoming calls", "Answer or reject by voice", announceCalls) {
            announceCalls = it; prefs.announceCalls = it
        }
        Toggle("Answer on speaker", value = speaker) { speaker = it; prefs.speakerOnAnswer = it }
        Toggle("Read new messages aloud", value = announceMsgs) { announceMsgs = it; prefs.announceMessages = it }
        Toggle("Favourites ring in DND", "Starred contacts can still reach you", starred) {
            starred = it; prefs.dndStarredRing = it
        }
        VSpace(6.dp)
        Field(reply, { reply = it; prefs.dndReply = it.ifBlank { Dnd.DEFAULT_REPLY } }, "DND auto-reply")
    }

    @Composable
    private fun PermRow(label: String, ok: Boolean, fix: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (ok) U.goldBrush else Brush.linearGradient(listOf(U.Glass2, U.Glass2)))
                    .border(1.dp, if (ok) Color.Transparent else U.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (ok) Text("✓", color = U.OnGold, style = MaterialTheme.typography.labelMedium) }
            HSpace(12.dp)
            Text(label, Modifier.weight(1f), color = if (ok) U.Ivory else U.Muted, style = MaterialTheme.typography.bodyMedium)
            if (!ok) TextButton(onClick = fix) { Text("Allow", color = U.Gold) }
        }
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        try {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ------------------------------------------------------------------ chat
    private data class Suggestion(val label: String, val prompt: String)

    private val suggestions = listOf(
        Suggestion("▶  Play Kesariya", "play Kesariya"),
        Suggestion("✉  Read my messages", "read my messages"),
        Suggestion("🖥  Read my PC screen", "read the screen"),
        Suggestion("⏰  Alarm for 6:30 am", "set an alarm for 6:30 am"),
        Suggestion("🌙  Go to DND mode", "go to DND mode"),
        Suggestion("📶  Who's on my Wi-Fi?", "how many devices are connected to my wifi"),
    )

    private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up?"
    }

    @Composable
    private fun ChatScreen(onSettings: () -> Unit) {
        val client = app.client
        val messages by client.messages.collectAsStateWithLifecycle()
        val conn by client.conn.collectAsStateWithLifecycle()
        val where by client.where.collectAsStateWithLifecycle()
        val busy by client.busy.collectAsStateWithLifecycle()
        val confirm by client.confirm.collectAsStateWithLifecycle()
        val voice by VoiceTurn.state.collectAsStateWithLifecycle()
        val level by VoiceTurn.level.collectAsStateWithLifecycle()
        val heard by VoiceTurn.heard.collectAsStateWithLifecycle()
        val wakeOn by WakeService.active.collectAsStateWithLifecycle()
        val wakeStatus by WakeService.status.collectAsStateWithLifecycle()
        val talk by talkRequest.collectAsStateWithLifecycle()
        val dndOn by Dnd.on.collectAsStateWithLifecycle()
        var input by remember { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }
        var progress by remember { mutableFloatStateOf(0f) }
        var note by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val list = rememberLazyListState()
        val haptic = LocalHapticFeedback.current
        val levels = remember { mutableStateListOf<Float>() }
        val seen = remember { HashSet<Long>().apply { messages.forEach { add(it.id) } } }

        LaunchedEffect(Unit) {
            VoiceTurn.level.collect { v ->
                levels.add(v)
                while (levels.size > 42) levels.removeAt(0)
            }
        }

        fun talkHere() {
            if (voice.isNotEmpty()) return
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (WakeService.active.value) {                   // the background listener owns the mic
                WakeService.start(this@MainActivity, talk = true)
                return
            }
            scope.launch {
                val rec = try { Recorder.open() } catch (e: Exception) { note = e.message ?: "mic error"; return@launch }
                try {
                    rec.startRecording()
                    VoiceTurn.beepStart()
                    VoiceTurn.run(rec, waitMs = 7000, flush = false) { false }
                } finally {
                    rec.stop(); rec.release()
                }
            }
        }

        fun send(text: String) {
            val t = text.trim()
            if (t.isEmpty()) return
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { client.ask(t) }
        }

        val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.RECORD_AUDIO] == true) talkHere() else note = "Ultron needs the microphone for voice."
        }
        val askWakePerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.RECORD_AUDIO] == true) {
                app.prefs.wake = true
                WakeService.start(this@MainActivity)
            } else note = "Ultron needs the microphone to hear “Hey Ultron”."
        }
        fun perms(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.RECORD_AUDIO)

        fun setWake(on: Boolean) {
            if (!on) {
                app.prefs.wake = false
                WakeService.stop(this@MainActivity)
                return
            }
            if (!VoskModel.ready(this@MainActivity)) {
                downloading = true
                scope.launch {
                    try {
                        VoskModel.download(this@MainActivity) { progress = it }
                        askWakePerms.launch(perms())
                    } catch (e: Exception) {
                        note = "Couldn't download the wake-word model: ${e.message}"
                    }
                    downloading = false
                }
            } else askWakePerms.launch(perms())
        }

        LaunchedEffect(talk) { if (talk > 0) { if (hasMic()) talkHere() else askMic.launch(perms()) } }
        val lastLen = messages.lastOrNull()?.text?.length ?: 0
        LaunchedEffect(messages.size, lastLen, busy) {
            val n = messages.size + (if (busy) 1 else 0)
            if (n > 0) list.animateScrollToItem(n - 1)
        }

        val orbState = voice.ifEmpty { if (busy) "thinking" else "" }

        Aurora {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                // ---- header
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    UltronOrb(size = 46.dp, state = orbState, level = level)
                    HSpace(10.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Ultron", style = MaterialTheme.typography.titleLarge, color = U.Ivory)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (txt, col) = when (conn) {
                                UltronClient.Conn.ONLINE -> "Online · $where" to U.Sage
                                UltronClient.Conn.CONNECTING -> "Connecting…" to U.Gold
                                UltronClient.Conn.OFFLINE -> "Can't reach the computer" to U.Coral
                            }
                            PulseDot(col, 6.dp)
                            Text(txt, color = U.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    GlassIconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); client.newChat() }) {
                        Icon(Icons.Filled.Add, "New chat", tint = U.Ivory)
                    }
                    HSpace(8.dp)
                    GlassIconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = U.Ivory) }
                }

                // ---- "Hey Ultron" card
                Glass(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), radius = 20.dp, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniEq(active = wakeOn)
                        HSpace(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text("“Hey Ultron”", color = U.Ivory, style = MaterialTheme.typography.titleMedium)
                            val sub = when {
                                downloading -> "Getting the listening model ready… ${(progress * 100).toInt()}%"
                                wakeOn -> wakeStatus.ifBlank { "Listening in the background" }
                                else -> "Off — tap the mic to talk"
                            }
                            AnimatedContent(sub, transitionSpec = {
                                (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith fadeOut(tween(150))
                            }, label = "sub") { s -> Text(s, color = U.Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        Switch(
                            checked = wakeOn || downloading, enabled = !downloading, onCheckedChange = { setWake(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = U.OnGold, checkedTrackColor = U.Gold, checkedBorderColor = U.Gold,
                                uncheckedThumbColor = U.Muted, uncheckedTrackColor = U.Glass, uncheckedBorderColor = U.Hairline,
                            ),
                        )
                    }
                    AnimatedVisibility(downloading) {
                        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(U.Glass)) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(4.dp).background(U.goldBrush))
                        }
                    }
                }

                // ---- DND banner
                AnimatedVisibility(dndOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(U.Bronze.copy(alpha = 0.55f), U.Glass2)))
                            .border(1.dp, U.Gold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🌙  DND on — auto-replying", color = U.GoldLight, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                val summary = withContext(Dispatchers.IO) { Dnd.stop(this@MainActivity) }
                                client.add("ultron", summary)
                                if (app.prefs.speak) app.speaker.say(summary)
                            }
                        }) { Text("I'm back", color = U.Ivory) }
                    }
                }
                AnimatedVisibility(note.isNotBlank()) {
                    Text(note, color = U.Coral, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp).clickable { note = "" })
                }

                // ---- conversation
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty() && !busy) {
                        EmptyState { send(it) }
                    } else {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { it.id }) { m ->
                                val fresh = remember(m.id) { seen.add(m.id) }
                                Box(Modifier.animateItem()) {
                                    MessageItem(m, animate = fresh,
                                        streaming = busy && m.role == "ultron" && m.id == messages.last().id)
                                }
                            }
                            if (busy && messages.lastOrNull()?.role != "ultron") item(key = "typing") {
                                Box(Modifier.animateItem()) {
                                    Reveal {
                                        Box(Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(U.Glass2)
                                            .border(1.dp, U.Hairline, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                                            .padding(horizontal = 18.dp, vertical = 14.dp)) { TypingDots() }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- voice panel
                AnimatedVisibility(
                    voice.isNotEmpty(),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Glass(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), radius = 26.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UltronOrb(size = 84.dp, state = voice, level = level)
                            HSpace(14.dp)
                            Column(Modifier.weight(1f)) {
                                Eyebrow(when (voice) { "listening" -> "Listening"; "thinking" -> "Thinking"; else -> "Speaking" })
                                VSpace(4.dp)
                                AnimatedContent(heard.ifBlank {
                                    if (voice == "listening") "Go ahead, I'm all ears…" else "…"
                                }, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "heard") { h ->
                                    Text(h, color = U.Ivory, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                                }
                                if (voice == "listening") {
                                    VSpace(8.dp)
                                    Waveform(levels.toList(), Modifier.fillMaxWidth().height(34.dp))
                                }
                            }
                        }
                    }
                }

                // ---- input bar
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(28.dp))
                            .background(U.Glass2.copy(alpha = 0.92f)).border(1.dp, U.Hairline, RoundedCornerShape(28.dp)),
                    ) {
                        TextField(
                            input, { input = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ask Ultron anything…", color = U.Faint) },
                            maxLines = 5, textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = U.Gold, focusedTextColor = U.Ivory, unfocusedTextColor = U.Ivory,
                            ),
                        )
                    }
                    HSpace(10.dp)
                    val mode = when { busy -> "stop"; input.isNotBlank() -> "send"; else -> "mic" }
                    ActionButton(mode, listening = voice == "listening") {
                        when (mode) {
                            "stop" -> { client.stop(); app.speaker.stop() }
                            "send" -> { send(input); input = "" }
                            else -> if (hasMic()) talkHere() else askMic.launch(perms())
                        }
                    }
                }
            }
        }

        confirm?.let { c ->
            AlertDialog(
                onDismissRequest = {},
                containerColor = U.Glass2,
                shape = RoundedCornerShape(26.dp),
                title = { Text("Ultron needs your OK", color = U.Ivory, style = MaterialTheme.typography.titleLarge) },
                text = { Text(c.detail, color = U.Muted, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { GoldButton("Approve", Modifier.widthIn(min = 120.dp)) { client.answer(c.id, true) } },
                dismissButton = { TextButton(onClick = { client.answer(c.id, false) }) { Text("Deny", color = U.Muted) } },
            )
        }
    }

    @Composable
    private fun EmptyState(onPick: (String) -> Unit) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Reveal(0) { UltronOrb(size = 200.dp) }
            VSpace(18.dp)
            Reveal(1) { Eyebrow(greeting()) }
            VSpace(6.dp)
            Reveal(2) {
                Text("What shall we do?", style = MaterialTheme.typography.displaySmall, color = U.Ivory,
                    textAlign = TextAlign.Center)
            }
            VSpace(8.dp)
            Reveal(3) {
                Text("Type, tap the mic, or just say “Hey Ultron”.", color = U.Muted,
                    style = MaterialTheme.typography.bodyMedium)
            }
            VSpace(26.dp)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(suggestions.size) { i ->
                    Reveal(4 + i) { Chip(suggestions[i].label) { onPick(suggestions[i].prompt) } }
                }
            }
        }
    }

    @Composable
    private fun MessageItem(m: ChatMsg, animate: Boolean, streaming: Boolean) {
        val p = remember(m.id) { Animatable(if (animate) 0f else 1f) }
        LaunchedEffect(m.id) { if (animate) p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
        val mine = m.role == "you"
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = p.value.coerceIn(0f, 1f)
                translationY = (1f - p.value) * 40f
                translationX = (1f - p.value) * (if (mine) 30f else -30f)
                val s = 0.94f + 0.06f * p.value
                scaleX = s; scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
            },
            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            when (m.role) {
                "you" -> Text(
                    m.text, color = U.OnGold, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                        .background(U.goldSoft).padding(horizontal = 16.dp, vertical = 12.dp),
                )
                "ultron" -> Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(22.dp).clip(CircleShape).background(
                        Brush.radialGradient(listOf(U.GoldLight, U.Gold, U.Bronze))))
                    HSpace(8.dp)
                    SelectionContainer {
                        Text(
                            Speaker.forDisplay(m.text) + if (streaming) "  ▍" else "",
                            color = U.Ivory, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                                .background(U.Glass2.copy(alpha = 0.94f))
                                .border(1.dp, U.Hairline, RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                "tool" -> Row(
                    Modifier.padding(start = 30.dp).clip(RoundedCornerShape(50)).background(U.Glass.copy(alpha = 0.8f))
                        .border(1.dp, U.Hairline, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner()
                    HSpace(8.dp)
                    Text(m.text.removePrefix("⚙ ").replaceFirstChar { it.uppercase() }, color = U.Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                "error" -> Text(m.text, color = U.Coral, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 30.dp).clip(RoundedCornerShape(12.dp))
                        .background(U.Coral.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp))
                else -> Text(m.text, color = U.Muted, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(start = 30.dp))
            }
        }
    }

    @Composable
    private fun Spinner() {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
        Canvas(Modifier.size(12.dp)) {
            drawArc(U.Gold, a, 260f, false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }

    /** Mic ↔ send ↔ stop, morphing, with a breathing halo while listening. */
    @Composable
    private fun ActionButton(mode: String, listening: Boolean, onClick: () -> Unit) {
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val press by androidx.compose.animation.core.animateFloatAsState(
            if (pressed) 0.88f else 1f, spring(0.45f, 600f), label = "press")
        val t = rememberInfiniteTransition(label = "halo")
        val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "h")
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (listening) {
                Canvas(Modifier.size(64.dp)) {
                    drawCircle(U.Gold.copy(alpha = (1f - halo) * 0.45f), radius = size.minDimension / 2 * (0.8f + 0.2f * halo))
                }
            }
            Box(
                Modifier.size(54.dp).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape)
                    .background(if (mode == "stop") Brush.linearGradient(listOf(U.Glass2, U.Glass)) else U.goldBrush)
                    .border(1.dp, if (mode == "stop") U.Gold.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .clickable(src, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(mode, transitionSpec = {
                    (scaleIn(spring(0.5f, 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                        (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)))
                }, label = "icon") { m ->
                    val (res, tint) = when (m) {
                        "stop" -> R.drawable.ic_stop to U.Gold
                        "send" -> R.drawable.ic_send to U.OnGold
                        else -> R.drawable.ic_mic to U.OnGold
                    }
                    Icon(painterResource(res), m, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
