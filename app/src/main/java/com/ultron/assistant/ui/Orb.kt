package com.ultron.assistant.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The living Ultron orb: a champagne-gold core that breathes when idle, sends out ripples while listening,
 * spins an arc while thinking and pulses rays while speaking. [level] (0..1) is the microphone level.
 */
@Composable
fun UltronOrb(modifier: Modifier = Modifier, size: Dp = 160.dp, state: String = "", level: Float = 0f) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val fast by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "fast")
    val breathe by t.animateFloat(0.94f, 1.04f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ripple")
    val wave by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wave")
    val voice by animateFloatAsState(level, spring(dampingRatio = 0.55f, stiffness = 300f), label = "voice")
    val energy by animateFloatAsState(
        when (state) { "listening" -> 1f; "thinking" -> 0.7f; "speaking" -> 0.85f; else -> 0.35f },
        tween(600), label = "energy")

    Canvas(modifier.size(size)) {
        val c = center
        val r = min(this.size.width, this.size.height) / 2f
        val core = r * 0.42f * (breathe + voice * 0.35f)

        // soft glow
        drawCircle(
            Brush.radialGradient(
                listOf(U.Gold.copy(alpha = 0.30f + 0.25f * energy), U.Bronze.copy(alpha = 0.10f), Color.Transparent),
                center = c, radius = r * (0.95f + voice * 0.1f)),
            radius = r, center = c)

        // listening: rings flowing outwards
        if (state == "listening") {
            for (k in 0..2) {
                val p = (ripple + k / 3f) % 1f
                drawCircle(U.GoldLight.copy(alpha = (1f - p) * 0.45f), radius = core + (r - core) * p, center = c,
                    style = Stroke(width = 2.dp.toPx()))
            }
        }

        // the turning halo
        rotate(spin, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, U.Gold.copy(alpha = 0.9f), U.GoldLight,
                    Color.Transparent, U.GoldDeep.copy(alpha = 0.7f), Color.Transparent), center = c),
                radius = r * 0.66f, center = c, style = Stroke(width = 1.6.dp.toPx()))
        }

        // thinking: two arcs chasing each other
        if (state == "thinking") {
            val d = r * 1.5f
            val tl = Offset(c.x - d / 2, c.y - d / 2)
            drawArc(U.GoldLight, fast, 70f, false, tl, Size(d, d), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(U.Gold.copy(alpha = 0.6f), -fast * 0.7f + 180f, 40f, false, tl, Size(d, d),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        // speaking: rays around the core
        if (state == "speaking") {
            val n = 36
            for (i in 0 until n) {
                val a = (i * 2 * PI / n).toFloat()
                val amp = 0.5f + 0.5f * sin(wave * 2 + i * 0.9f)
                val inner = core * 1.15f
                val outer = inner + r * 0.18f * amp
                drawLine(U.Gold.copy(alpha = 0.35f + 0.5f * amp),
                    Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                    Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        // the core
        drawCircle(
            Brush.radialGradient(listOf(U.Ivory, U.GoldLight, U.Gold, U.GoldDeep, U.Bronze),
                center = Offset(c.x - core * 0.3f, c.y - core * 0.35f), radius = core * 1.5f),
            radius = core, center = c)
        // glossy highlight
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(c.x - core * 0.35f, c.y - core * 0.4f), radius = core * 0.55f),
            radius = core * 0.55f, center = Offset(c.x - core * 0.35f, c.y - core * 0.4f))
    }
}
