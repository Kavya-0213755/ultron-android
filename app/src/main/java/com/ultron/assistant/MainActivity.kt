package com.ultron.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.NotificationManager
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Red = Color(0xFFE0322B)
private val Bg = Color(0xFF0B0F1A)
private val Card = Color(0xFF161C2B)
private val Mine = Color(0xFF3A1414)

class MainActivity : ComponentActivity() {
    private val app get() = UltronApp.instance
    private val talkRequest = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root() }
            }
        }
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
        when (screen) {
            "setup" -> SetupScreen(first = app.prefs.token.isBlank()) { screen = "chat" }
            else -> ChatScreen(onSettings = { screen = "setup" })
        }
    }

    // ------------------------------------------------------------------ setup / settings
    @OptIn(ExperimentalMaterial3Api::class)
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

        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Text(if (first) "Connect to Ultron" else "Settings") },
                    navigationIcon = {
                        if (!first) IconButton(onClick = done) { Icon(Icons.Filled.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Ultron runs on your computer. The phone connects to it at home over Wi-Fi, and from anywhere through Tailscale.",
                    color = Color.LightGray, fontSize = 14.sp)
                OutlinedTextField(home, { home = it }, label = { Text("At home (Wi-Fi address)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(away, { away = it }, label = { Text("Away (Tailscale address, optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("Access key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("Same key as the web app. On the computer:  grep auth_token ~/.config/ultron/config.yaml",
                    color = Color.Gray, fontSize = 12.sp)
                if (!first) {
                    SettingSwitch("Speak answers aloud", speak) { speak = it }
                    SettingSwitch("Keep listening for a follow-up after an answer", follow) { follow = it }
                    OutlinedButton(onClick = { askBatteryExemption() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Let Ultron run in the background (battery)")
                    }
                    PhonePowers()
                }
                Button(
                    enabled = !testing && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
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
                                msg = "Wrong access key."
                                return@launch
                            }
                            prefs.homeUrl = h; prefs.awayUrl = a; prefs.token = token
                            prefs.speak = speak; prefs.followUp = follow
                            app.client.restart()
                            if (res == null) done()
                            else msg = "Saved, but the computer didn't answer right now:\n$res"
                        }
                    },
                ) { Text(if (first) "Connect" else "Save") }
                if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFB4A9), fontSize = 13.sp)
                if (!first && msg.startsWith("Saved")) TextButton(onClick = done) { Text("Continue anyway") }
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

        Spacer(Modifier.height(8.dp))
        Text("Phone powers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Say things like “open Instagram”, “play Kesariya”, “set an alarm for 6:30”, “torch on”, “call Papa”, " +
            "“send a WhatsApp to Rahul saying I'm on my way”, “read my messages”, “go to DND mode and say I'm driving”, " +
            "“I'm back”. Add “on the computer” to do it on the PC instead.", color = Color.Gray, fontSize = 13.sp)
        PermRow("Calls, SMS and contacts", callsOk) { askPerms.launch(Phone.CALL_PERMS) }
        PermRow("Open apps & play music from the background (Display over other apps)", overlayOk) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        PermRow("Read & reply to messages (notification access)", listenOk) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermRow("Send WhatsApp hands-free (Accessibility → Ultron)", autoOk) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PermRow("DND can silence the phone", policyOk) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        Text("If Android says “Restricted setting”: open App info → ⋮ (top right) → “Allow restricted settings”, then try again.",
            color = Color.Gray, fontSize = 12.sp)
        TextButton(onClick = { Phone.openAppSettings(this@MainActivity) }) { Text("Open App info") }
        SettingSwitch("Announce incoming calls (answer or reject by voice)", announceCalls) {
            announceCalls = it; prefs.announceCalls = it
        }
        SettingSwitch("Put calls I answer by voice on speaker", speaker) { speaker = it; prefs.speakerOnAnswer = it }
        SettingSwitch("Read new messages aloud", announceMsgs) { announceMsgs = it; prefs.announceMessages = it }
        SettingSwitch("In DND, let favourite (starred) contacts ring", starred) { starred = it; prefs.dndStarredRing = it }
        OutlinedTextField(reply, { reply = it; prefs.dndReply = it.ifBlank { Dnd.DEFAULT_REPLY } },
            label = { Text("DND auto-reply") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    }

    @Composable
    private fun PermRow(label: String, ok: Boolean, fix: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text((if (ok) "✓ " else "○ ") + label, Modifier.weight(1f), color = if (ok) Color(0xFF7BD88F) else Color.White,
                fontSize = 14.sp)
            if (!ok) TextButton(onClick = fix) { Text("Allow") }
        }
    }

    @Composable
    private fun SettingSwitch(label: String, value: Boolean, change: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = Color.White)
            Switch(checked = value, onCheckedChange = change)
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
    @OptIn(ExperimentalMaterial3Api::class)
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

        fun talkHere() {
            if (voice.isNotEmpty()) return
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
        LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
            if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1)
        }

        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Ultron", fontWeight = FontWeight.Bold)
                            val (txt, col) = when (conn) {
                                UltronClient.Conn.ONLINE -> "Connected $where" to Color(0xFF7BD88F)
                                UltronClient.Conn.CONNECTING -> "Connecting…" to Color(0xFFFFC66D)
                                UltronClient.Conn.OFFLINE -> "Can't reach the computer" to Color(0xFFFF8A80)
                            }
                            Text(txt, fontSize = 12.sp, color = col)
                        }
                    },
                    actions = {
                        IconButton(onClick = { client.newChat() }) { Icon(Icons.Filled.Add, "New chat") }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
                // wake word switch
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Listen for “Hey Ultron”", color = Color.White, fontSize = 15.sp)
                        val sub = when {
                            downloading -> "Downloading the wake-word model (once, 36 MB)…"
                            wakeOn -> wakeStatus.ifBlank { "Listening in the background" }
                            else -> "Off — tap the mic to talk"
                        }
                        Text(sub, color = Color.Gray, fontSize = 12.sp)
                        if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Switch(checked = wakeOn || downloading, enabled = !downloading, onCheckedChange = { setWake(it) })
                }
                if (dndOn) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Mine).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔕 DND on — auto-replying", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch {
                            val summary = withContext(Dispatchers.IO) { Dnd.stop(this@MainActivity) }
                            client.add("ultron", summary)
                            if (app.prefs.speak) app.speaker.say(summary)
                        }
                    }) { Text("Turn off") }
                }
                if (note.isNotBlank()) Text(note, color = Color(0xFFFFB4A9), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (messages.isEmpty()) item {
                        Text("Ask anything, or say “Hey Ultron”.\nTry: “open YouTube on the computer”, “what's on my screen?”, “how many devices are on my Wi-Fi?”",
                            color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp, start = 8.dp))
                    }
                    items(messages, key = { it.id }) { m -> Bubble(m) }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                if (voice.isNotEmpty()) {
                    val label = when (voice) {
                        "listening" -> "Listening…"
                        "thinking" -> "Thinking…"
                        else -> "Speaking…"
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(label, color = Red, fontSize = 13.sp)
                        if (voice == "listening") LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        input, { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Ultron…") }, maxLines = 5,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    when {
                        busy -> FilledIconButton(onClick = { client.stop(); app.speaker.stop() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Card)) {
                            Icon(painterResource(R.drawable.ic_stop), "Stop")
                        }
                        input.isNotBlank() -> FilledIconButton(onClick = {
                            val t = input.trim()
                            input = ""
                            scope.launch { client.ask(t) }
                        }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) {
                            Icon(painterResource(R.drawable.ic_send), "Send")
                        }
                        else -> FilledIconButton(
                            onClick = { if (hasMic()) talkHere() else askMic.launch(perms()) },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (voice == "listening") Color.White else Red),
                        ) {
                            Icon(painterResource(R.drawable.ic_mic), "Talk",
                                tint = if (voice == "listening") Red else Color.White)
                        }
                    }
                }
            }
        }

        confirm?.let { c ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Ultron needs your OK") },
                text = { Text(c.detail) },
                confirmButton = { Button(onClick = { client.answer(c.id, true) }) { Text("Approve") } },
                dismissButton = { TextButton(onClick = { client.answer(c.id, false) }) { Text("Deny") } },
            )
        }
    }

    @Composable
    private fun Bubble(m: ChatMsg) {
        when (m.role) {
            "you" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(m.text, color = Color.White, modifier = Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Mine).padding(12.dp))
            }
            "ultron" -> SelectionContainer {
                Text(Speaker.forDisplay(m.text), color = Color(0xFFEDEDED), modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(Card).padding(12.dp))
            }
            "error" -> Text("⚠ " + m.text, color = Color(0xFFFF8A80), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
            else -> Text(m.text, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
