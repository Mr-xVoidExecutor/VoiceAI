package com.voiceai.app

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

val CYAN = Color(0xFF22E6D6)
private val ICE = Color(0xFFB8FFF8)
private const val CH = "01アイウエオカキクケコ{}<>/=+*#ABCDEF0123456789অআইউএও"

@Composable
fun Rain(t: State<Float>, modifier: Modifier = Modifier) {
    val paint = remember { android.graphics.Paint().apply { textSize = 32f; isAntiAlias = true; typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        val cw = 36f
        val cols = (size.width / cw).toInt()
        val tt = t.value
        drawIntoCanvas { c ->
            for (i in 0 until cols) {
                val dir = if (i % 3 == 0) -1f else 1f
                val sp = 70f + (i * 37 % 90)
                val head = ((tt * sp * dir + i * 211f) % size.height + size.height) % size.height
                for (k in 0 until 9) {
                    val y = head - dir * k * 36f
                    paint.color = (if (k == 0) 0xFFB8FFF8 else 0xFF22E6D6).toInt()
                    paint.alpha = (70 * (1f - k / 9f)).toInt().coerceAtLeast(4)
                    val ch = CH[((tt * 5).toInt() + i * 7 + k * 3) % CH.length]
                    c.nativeCanvas.drawText(ch.toString(), i * cw, y, paint)
                }
            }
        }
    }
}

@Composable
fun Angel(t: State<Float>, state: S, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val amp = when (state) { S.SPEAKING -> 1f; S.LISTENING -> 0.55f; S.THINKING -> 0.3f; else -> 0.12f }
        val pulse = (sin(tt * (if (state == S.SPEAKING) 9f else 2.2f)) + 1f) / 2f
        val sc = min(size.width / 100f, size.height / 120f)
        val ox = (size.width - 100f * sc) / 2f
        val oy = (size.height - 120f * sc) / 2f
        withTransform({ translate(ox, oy); scale(sc, Offset.Zero) }) {
            val ar = 52f + 6f * amp * pulse
            drawCircle(Brush.radialGradient(listOf(CYAN.copy(0.30f), CYAN.copy(0.08f), Color.Transparent), Offset(50f, 62f), ar), ar, Offset(50f, 62f))
            val flap = sin(tt * 2.2f) * (1f + amp)
            for (m in 0..1) {
                fun X(x: Float) = if (m == 0) x else 100f - x
                val w = Path().apply {
                    moveTo(X(45f), 52f)
                    cubicTo(X(30f), 34f + flap, X(12f), 24f + flap, X(2f), 4f + flap * 2)
                    cubicTo(X(6f), 30f, X(4f), 50f, X(10f), 66f)
                    cubicTo(X(16f), 80f, X(32f), 84f, X(45f), 76f)
                    close()
                }
                drawPath(w, Brush.verticalGradient(listOf(CYAN.copy(0.35f), CYAN.copy(0.04f)), 0f, 84f))
                drawPath(w, CYAN.copy(0.18f), style = Stroke(5f))
                drawPath(w, CYAN.copy(0.9f), style = Stroke(1.2f))
                val exs = listOf(3f, 5f, 7f, 12f, 26f)
                val eys = listOf(6f, 24f, 42f, 62f, 78f)
                for (k in 0..4) {
                    val f = Path().apply {
                        moveTo(X(45f), 56f)
                        quadraticBezierTo(X((45f + exs[k]) / 2f), (56f + eys[k]) / 2f - 6f, X(exs[k]), eys[k] + (if (k < 2) flap * 2 else 0f))
                    }
                    drawPath(f, ICE.copy(0.55f), style = Stroke(0.8f))
                }
            }
            val body = Path().apply {
                moveTo(44f, 45f); cubicTo(40f, 49f, 31f, 52f, 29f, 63f); cubicTo(27f, 76f, 21f, 96f, 15f, 116f)
                lineTo(85f, 116f); cubicTo(79f, 96f, 73f, 76f, 71f, 63f); cubicTo(69f, 52f, 60f, 49f, 56f, 45f); close()
            }
            drawPath(body, Brush.verticalGradient(listOf(ICE.copy(0.55f), CYAN.copy(0.12f)), 45f, 116f))
            drawPath(body, CYAN.copy(0.8f), style = Stroke(1.1f))
            for (m in 0..1) {
                fun X(x: Float) = if (m == 0) x else 100f - x
                val sw = sin(tt * 1.8f + m) * (1.5f + amp * 2f)
                val h = Path().apply {
                    moveTo(X(41f), 20f)
                    cubicTo(X(30f + sw), 26f, X(32f + sw), 52f, X(25f + sw * 1.5f), 84f)
                    cubicTo(X(36f), 70f, X(42f), 52f, X(43f), 38f)
                    close()
                }
                drawPath(h, ICE.copy(0.6f))
                drawPath(h, CYAN.copy(0.9f), style = Stroke(1f))
            }
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(0.95f), ICE.copy(0.7f)), 18f, 42f), Offset(41f, 19f), Size(18f, 23f))
            drawOval(CYAN, Offset(41f, 19f), Size(18f, 23f), style = Stroke(1f))
            val cap = Path().apply { moveTo(40f, 28f); cubicTo(38f, 12f, 62f, 12f, 60f, 28f); cubicTo(56f, 22f, 44f, 22f, 40f, 28f); close() }
            drawPath(cap, ICE.copy(0.85f))
            drawLine(CYAN, Offset(45f, 29f), Offset(48f, 29f), 0.8f)
            drawLine(CYAN, Offset(52f, 29f), Offset(55f, 29f), 0.8f)
            if (state == S.SPEAKING) drawOval(CYAN.copy(0.85f), Offset(47.5f, 35.5f), Size(5f, 1f + 2.5f * pulse))
            val hy = 6f + sin(tt * 1.5f)
            drawOval(CYAN.copy(0.25f), Offset(38f, hy), Size(24f, 6f), style = Stroke(4f))
            drawOval(ICE, Offset(38f, hy), Size(24f, 6f), style = Stroke(1.2f))
            val orb = 7f + 3f * pulse
            drawCircle(Brush.radialGradient(listOf(Color.White, CYAN.copy(0.6f), Color.Transparent), Offset(50f, 62f), orb), orb, Offset(50f, 62f))
            for (i in 0 until 22) {
                val px = 10f + (i * 37 % 80)
                val py = 120f - ((tt * (8f + (i % 5) * 3f) + i * 17f) % 120f)
                drawCircle(ICE.copy(0.6f), 0.7f, Offset(px + sin(tt + i) * 2f, py))
            }
            var y = 0f
            while (y < 120f) { drawLine(Color.Black.copy(0.18f), Offset(0f, y), Offset(100f, y), 0.9f); y += 3f }
            drawRect(CYAN.copy(0.12f), Offset(0f, (tt * 40f) % 120f), Size(100f, 3f))
            val wv = Path().apply {
                for (i in 0..100) {
                    val x = i.toFloat()
                    val yy = 117f + sin(x * 0.35f + tt * 8f) * 3f * amp * sin(x / 100f * PI.toFloat())
                    if (i == 0) moveTo(x, yy) else lineTo(x, yy)
                }
            }
            drawPath(wv, CYAN, style = Stroke(0.8f))
        }
    }
}
