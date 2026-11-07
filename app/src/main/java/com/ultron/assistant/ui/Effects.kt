package com.ultron.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Slow-drifting champagne light behind everything. */
@Composable
fun Aurora(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Box(
        modifier.fillMaxSize().background(U.Bg).drawBehind {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(U.Gold.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.5f * a), h * (0.12f + 0.1f * b)), radius = w * 0.8f),
                radius = w * 0.8f, center = Offset(w * (0.15f + 0.5f * a), h * (0.12f + 0.1f * b)))
            drawCircle(
                Brush.radialGradient(listOf(U.Bronze.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * (0.9f - 0.4f * b), h * (0.75f - 0.15f * a)), radius = w * 0.9f),
                radius = w * 0.9f, center = Offset(w * (0.9f - 0.4f * b), h * (0.75f - 0.15f * a)))
            drawCircle(
                Brush.radialGradient(listOf(Color(0x0FE8E6E1), Color.Transparent),
                    center = Offset(w * 0.5f, h * (0.45f + 0.1f * a)), radius = w * 0.6f),
                radius = w * 0.6f, center = Offset(w * 0.5f, h * (0.45f + 0.1f * a)))
        },
        content = content,
    )
}

/** Frosted card with a hairline border. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 22.dp, padding: Dp = 16.dp,
          content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(U.Glass2.copy(alpha = 0.92f), U.Glass.copy(alpha = 0.88f))))
            .border(1.dp, U.Hairline, RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

/** Things fade and rise into place, one after another ([index] staggers them). */
@Composable
fun Reveal(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        p.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 220f))
    }
    Box(modifier.graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 36f
        val s = 0.96f + 0.04f * p.value
        scaleX = s; scaleY = s
    }) { content() }
}

/** A breathing status dot. */
@Composable
fun PulseDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Canvas(Modifier.size(size * 2.4f)) {
        val r = size.toPx() / 2
        drawCircle(color.copy(alpha = (1f - p) * 0.5f), radius = r + r * 1.4f * p)
        drawCircle(color, radius = r)
    }
}

/** Three dots bouncing while Ultron thinks. */
@Composable
fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(18.dp)) {
        for (i in 0..2) {
            val d = ((p - i + 3f) % 3f)
            val lift = if (d < 1f) kotlin.math.sin(d * Math.PI).toFloat() else 0f
            Box(Modifier.graphicsLayer { translationY = -lift * 10f; alpha = 0.45f + 0.55f * lift }
                .size(7.dp).clip(CircleShape).background(U.Gold))
        }
    }
}

/** Live microphone bars. */
@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier, color: Color = U.Gold) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val n = levels.size
        val gap = size.width / n
        val mid = size.height / 2
        levels.forEachIndexed { i, v ->
            val h = (0.08f + v.coerceIn(0f, 1f) * 0.92f) * size.height * 0.5f
            val x = gap * i + gap / 2
            drawLine(color.copy(alpha = 0.35f + 0.65f * v.coerceIn(0f, 1f)), Offset(x, mid - h), Offset(x, mid + h),
                strokeWidth = gap * 0.55f, cap = StrokeCap.Round)
        }
    }
}

/** Little equaliser that dances while "Hey Ultron" is listening. */
@Composable
fun MiniEq(active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val p by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "p")
    Canvas(modifier.size(22.dp, 16.dp)) {
        val bars = 4
        val w = size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            val v = if (active) 0.3f + 0.7f * (0.5f + 0.5f * kotlin.math.sin(p * (1 + i * 0.35f) + i)) else 0.25f
            val h = size.height * v
            drawRoundRect(if (active) U.Gold else U.Faint, Offset(i * 2 * w, size.height - h),
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2))
        }
    }
}

/** The big champagne button with a light sweep and a springy press. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.5f, 500f), label = "s")
    val t = rememberInfiniteTransition(label = "shine")
    val shine by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)), label = "x")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(18.dp)).background(U.goldBrush)
            .drawBehind {
                val x = size.width * shine
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                    start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)))
            }
            .clickable(src, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = U.OnGold, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/** Round glass icon button with a springy press. */
@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 42.dp, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.45f, 600f), label = "s")
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(U.Glass2.copy(alpha = 0.9f)).border(1.dp, U.Hairline, CircleShape)
            .clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A tappable suggestion pill. */
@Composable
fun Chip(text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.5f, 600f), label = "s")
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(50))
            .background(U.Glass2.copy(alpha = 0.85f))
            .border(1.dp, Brush.linearGradient(listOf(U.Gold.copy(alpha = 0.55f), U.Hairline)), RoundedCornerShape(50))
            .clickable(src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = U.Ivory, style = MaterialTheme.typography.labelLarge) }
}

/** Small uppercase gold label. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = U.Gold, style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

@Composable
fun VSpace(h: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.width(w))
