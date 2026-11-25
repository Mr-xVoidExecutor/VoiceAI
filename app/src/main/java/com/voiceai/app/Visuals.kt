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
import android.os.Build
import android.graphics.Shader
import android.graphics.RuntimeShader
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.sin

val CYAN = Color(0xFF22E6D6)
private val ICE = Color(0xFFB8FFF8)
private const val CH = "01アイウエオカキクケコ{}<>/=+*#ABCDEF0123456789অআইউএও"

@Composable
fun Rain(t: State<Float>, modifier: Modifier = Modifier) {
    val paint = remember { android.graphics.Paint().apply { textSize = 32f; isAntiAlias = true; typeface = Typeface.MONOSPACE } }
    val chars = remember { CH.map { it.toString() } }
    Canvas(modifier) {
        val cw = 36f
        val cols = (size.width / cw).toInt()
        val tt = t.value
        drawIntoCanvas { c ->
            for (i in 0 until cols) {
                val dir = if (i % 3 == 0) -1f else 1f
                val sp = 70f + (i * 37 % 90)
                val head = ((tt * sp * dir + i * 211f) % size.height + size.height) % size.height
                for (k in 0 until 7) {
                    val y = head - dir * k * 36f
                    paint.color = (if (k == 0) 0xFFB8FFF8 else 0xFF22E6D6).toInt()
                    paint.alpha = (70 * (1f - k / 7f)).toInt().coerceAtLeast(4)
                    c.nativeCanvas.drawText(chars[((tt * 5).toInt() + i * 7 + k * 3) % chars.size], i * cw, y, paint)
                }
            }
        }
    }
}

@Composable
fun Angel(t: State<Float>, state: S, modifier: Modifier = Modifier) {
    val body = remember { Path().apply {
        moveTo(44f, 45f); cubicTo(40f, 49f, 31f, 52f, 29f, 63f); cubicTo(27f, 76f, 21f, 96f, 15f, 116f)
        lineTo(85f, 116f); cubicTo(79f, 96f, 73f, 76f, 71f, 63f); cubicTo(69f, 52f, 60f, 49f, 56f, 45f); close() } }
    val cap = remember { Path().apply { moveTo(40f, 28f); cubicTo(38f, 12f, 62f, 12f, 60f, 28f); cubicTo(56f, 22f, 44f, 22f, 40f, 28f); close() } }
    Canvas(modifier) {
        val tt = t.value
        val amp = when (state) { S.SPEAKING -> 1f; S.LISTENING -> 0.55f; S.THINKING -> 0.3f; else -> 0.12f }
        val pulse = (sin(tt * (if (state == S.SPEAKING) 9f else 2.2f)) + 1f) / 2f
        val sc = min(size.width / 100f, size.height / 120f)
        val ox = (size.width - 100f * sc) / 2f
        val oy = (size.height - 120f * sc) / 2f
        withTransform({ translate(ox, oy); scale(sc, sc, Offset.Zero) }) {
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

fun Modifier.chamfer(cut: Float = 40f, col: Color = CYAN) = this.drawBehind {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(cut, 0f); lineTo(w - cut, 0f); lineTo(w, cut); lineTo(w, h - cut)
        lineTo(w - cut, h); lineTo(cut, h); lineTo(0f, h - cut); lineTo(0f, cut); close()
    }
    drawPath(p, col.copy(0.06f)); drawPath(p, col.copy(0.25f), style = Stroke(8f)); drawPath(p, col, style = Stroke(2.5f))
}

private const val AGSL = """
uniform float2 res; uniform float time; uniform float amp;
half4 main(float2 fc) {
  float2 p = (fc / res - 0.5) * float2(res.x / res.y, 1.0);
  float v = 0.0;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    p += 0.22 * sin(p.yx * (2.0 + fi) + time * (0.5 + 0.12 * fi) + fi);
    v += 0.012 / (abs(length(p) - 0.32 - 0.04 * fi) + 0.015);
  }
  float3 col = float3(0.10, 0.90, 0.84) * v * (0.35 + 0.5 * amp);
  float a = clamp(v * 0.35, 0.0, 0.9);
  return half4(half3(col * a), half(a));
}"""

private fun makeShader(): Shader? =
    if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(AGSL) as Shader }.getOrNull() else null

private fun setU(sh: Shader, w: Float, h: Float, t: Float, amp: Float) {
    if (Build.VERSION.SDK_INT >= 33) (sh as RuntimeShader).apply {
        setFloatUniform("res", w, h); setFloatUniform("time", t); setFloatUniform("amp", amp)
    }
}

@Composable
fun Aurora(t: State<Float>, amp: Float, modifier: Modifier = Modifier) {
    val shader = remember { makeShader() }
    Canvas(modifier) {
        if (shader != null) {
            setU(shader, size.width, size.height, t.value, amp)
            drawRect(ShaderBrush(shader))
        } else {
            val r = size.minDimension * 0.6f
            drawCircle(Brush.radialGradient(listOf(CYAN.copy(0.25f), Color.Transparent), center, r), r, center)
        }
    }
}

@Composable
fun Globe(t: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.9f
        val c = center
        val tt = t.value
        drawCircle(CYAN.copy(0.08f), r, c); drawCircle(CYAN, r, c, style = Stroke(1.5f))
        for (i in -2..2) {
            val y = i * r / 3f
            val rr = sqrt(r * r - y * y)
            drawOval(CYAN.copy(0.5f), Offset(c.x - rr, c.y + y - rr * 0.18f), Size(rr * 2, rr * 0.36f), style = Stroke(1f))
        }
        for (i in 0 until 6) {
            val a = tt * 0.5f + i * PI.toFloat() / 6f
            val rx = abs(cos(a)) * r
            drawOval(CYAN.copy(0.5f), Offset(c.x - rx, c.y - r), Size(rx * 2, r * 2), style = Stroke(1f))
        }
    }
}

@Composable
fun Hub(t: State<Float>, state: S, mem: Int, modifier: Modifier = Modifier) {
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; textSize = 28f; typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        val labels = listOf("MIC", "BRAIN", "MEMORY", "VOICE")
        val on = listOf(state == S.LISTENING, state == S.THINKING, mem > 0, state == S.SPEAKING)
        val cx = size.width * 0.80f
        val cy = size.height / 2f
        drawCircle(CYAN.copy(0.2f), 46f, Offset(cx, cy)); drawCircle(CYAN, 46f, Offset(cx, cy), style = Stroke(2f))
        val x0 = 12f
        val x1 = size.width * 0.40f
        for (i in 0..3) {
            val y = size.height * (i + 0.5f) / 4f
            val col = if (on[i]) CYAN else CYAN.copy(0.3f)
            drawRect(col.copy(0.12f), Offset(x0, y - 22f), Size(x1 - x0, 44f))
            drawRect(col, Offset(x0, y - 22f), Size(x1 - x0, 44f), style = Stroke(2f))
            paint.color = col.toArgb()
            drawIntoCanvas { it.nativeCanvas.drawText(labels[i], x0 + 12f, y + 9f, paint) }
            val p = Path().apply { moveTo(x1, y); cubicTo(x1 + 80f, y, cx - 120f, cy, cx - 46f, cy) }
            drawPath(p, col, style = Stroke(2f))
            if (on[i]) {
                val k = (t.value * 0.8f + i * 0.25f) % 1f
                drawCircle(Color.White, 6f, Offset(x1 + (cx - 46f - x1) * k, y + (cy - y) * k))
            }
        }
    }
}
