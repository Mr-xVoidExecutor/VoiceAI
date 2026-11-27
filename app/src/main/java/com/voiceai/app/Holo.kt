package com.voiceai.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val HICE = Color(0xFFB8FFF8)
val PURPLE = Color(0xFF7B5CFF)
private val GREEN = Color(0xFF3DFF8A)
private val ORANGE = Color(0xFFFF6A3D)
private val BLUE = Color(0xFF2F8BFF)
private val PINK = Color(0xFFFF4D6D)

// ───────────────────────── Hair data ─────────────────────────

private class Strand(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float,
    val w: Float, val a: Float, val ph: Float, val front: Boolean
)

private class Hang(val x: Float, val len: Float, val ph: Float, val a: Float)

private fun makeHair(): List<Strand> {
    val r = Random(7)
    val l = ArrayList<Strand>()
    for (i in 0 until 80) {
        val left = i % 2 == 0
        val sx = 33f + r.nextFloat() * 10f
        val sy = 12f + r.nextFloat() * 28f
        val ex = -6f + r.nextFloat() * 32f
        val ey = 78f + r.nextFloat() * 52f
        val c1x = sx - 8f - r.nextFloat() * 16f
        val c1y = sy + 8f + r.nextFloat() * 12f
        val c2x = ex - 8f + r.nextFloat() * 16f
        val c2y = ey - 34f - r.nextFloat() * 10f
        fun mx(x: Float) = if (left) x else 100f - x
        l.add(
            Strand(mx(sx), sy, mx(ex), ey, mx(c1x), c1y, mx(c2x), c2y,
                0.22f + r.nextFloat() * 0.5f, 0.25f + r.nextFloat() * 0.6f,
                r.nextFloat() * 6.28f, i % 4 == 0)
        )
    }
    return l
}

private fun makeHang(): List<Hang> {
    val r = Random(21)
    val l = ArrayList<Hang>()
    for (i in 0 until 26) {
        val side = if (i % 2 == 0) 1 else -1
        val off = 4f + r.nextFloat() * 30f
        val x = if (side == 1) off else 100f - off
        l.add(Hang(x, 30f + r.nextFloat() * 60f, r.nextFloat() * 6.28f, 0.3f + r.nextFloat() * 0.5f))
    }
    return l
}

private fun bz(a: Float, b: Float, c: Float, d: Float, k: Float): Float {
    val u = 1f - k
    return u * u * u * a + 3f * u * u * k * b + 3f * u * k * k * c + k * k * k * d
}

// ───────────────────────── Circuit traces ─────────────────────────

private val FACE_C = listOf(
    floatArrayOf(50f, 10f, 50f, 31f),
    floatArrayOf(50f, 17f, 45f, 21f, 45f, 28f),
    floatArrayOf(50f, 13f, 42f, 15f, 38f, 20f, 38f, 28f),
    floatArrayOf(47f, 11f, 41f, 12f, 36f, 17f),
    floatArrayOf(36f, 34f, 33f, 38f, 33f, 48f, 36f, 56f, 41f, 63f),
    floatArrayOf(35f, 44f, 38f, 47f, 38f, 54f),
    floatArrayOf(40f, 50f, 40f, 56f, 43f, 59f)
)

private val BODY_C = listOf(
    floatArrayOf(50f, 92f, 50f, 130f),
    floatArrayOf(46f, 70f, 46f, 92f), floatArrayOf(48f, 70f, 48f, 94f),
    floatArrayOf(44f, 70f, 44f, 90f),
    floatArrayOf(44f, 95f, 40f, 100f, 40f, 110f, 34f, 116f, 34f, 130f),
    floatArrayOf(46f, 96f, 46f, 112f, 42f, 118f, 42f, 130f),
    floatArrayOf(38f, 96f, 30f, 100f, 24f, 108f, 22f, 130f),
    floatArrayOf(30f, 94f, 20f, 102f, 12f, 112f, 10f, 130f),
    floatArrayOf(42f, 88f, 30f, 92f, 20f, 98f)
)

private fun DrawScope.circuit(pts: FloatArray, mirror: Boolean, glow: Float) {
    val p = Path()
    var i = 0
    var lx = 0f
    var ly = 0f
    while (i < pts.size) {
        val x = if (mirror) 100f - pts[i] else pts[i]
        val y = pts[i + 1]
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        lx = x; ly = y
        i += 2
    }
    drawPath(p, CYAN.copy(0.22f * glow), style = Stroke(1.6f))
    drawPath(p, HICE.copy(0.85f * glow), style = Stroke(0.35f))
    drawCircle(HICE.copy(glow), 0.55f, Offset(lx, ly))
}

// ───────────────────────── Hologram face ─────────────────────────

@Composable
fun HoloFace(t: State<Float>, state: S, modifier: Modifier = Modifier) {
    val hair = remember { makeHair() }
    val hang = remember { makeHang() }
    val head = remember {
        Path().apply {
            moveTo(50f, 10f)
            cubicTo(66f, 10f, 70f, 28f, 68f, 42f)
            cubicTo(66f, 56f, 60f, 68f, 50f, 72f)
            cubicTo(40f, 68f, 34f, 56f, 32f, 42f)
            cubicTo(30f, 28f, 34f, 10f, 50f, 10f)
            close()
        }
    }
    val neck = remember {
        Path().apply { moveTo(43f, 62f); lineTo(40f, 94f); lineTo(60f, 94f); lineTo(57f, 62f); close() }
    }
    val shoulders = remember {
        Path().apply {
            moveTo(41f, 88f)
            cubicTo(26f, 92f, 8f, 98f, 2f, 118f)
            lineTo(0f, 130f); lineTo(100f, 130f); lineTo(98f, 118f)
            cubicTo(92f, 98f, 74f, 92f, 59f, 88f)
            close()
        }
    }
    Canvas(modifier) {
        val tt = t.value
        val speaking = state == S.SPEAKING
        val amp = when (state) { S.SPEAKING -> 1f; S.LISTENING -> 0.55f; S.THINKING -> 0.3f; else -> 0.12f }
        val pulse = (sin(tt * (if (speaking) 9f else 2.2f)) + 1f) / 2f
        val glow = 0.65f + 0.35f * sin(tt * 2f + amp)

        // background glow (blue centre, faint warm edge like the reference)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF1B5BC0).copy(0.55f), Color(0xFF0A1B3A).copy(0.35f), Color.Transparent),
                center, max(size.width, size.height) * 0.6f
            ), max(size.width, size.height) * 0.6f, center
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF8A3A3A).copy(0.22f), Color.Transparent),
                Offset(size.width * 0.98f, size.height * 0.35f), size.width * 0.7f
            ), size.width * 0.7f, Offset(size.width * 0.98f, size.height * 0.35f)
        )

        val sc = size.width / 100f * 1.12f
        val ox = (size.width - 100f * sc) / 2f
        val oy = size.height - 130f * sc
        val topY = -oy / sc

        withTransform({ translate(ox, oy); scale(sc, sc, Offset.Zero) }) {
            // hanging data strands from the top
            hang.forEach { h ->
                val sw = sin(tt * 0.6f + h.ph) * 2.5f
                val yEnd = 12f + h.len
                val p = Path().apply {
                    moveTo(h.x, topY)
                    cubicTo(h.x + sw, topY + (yEnd - topY) * 0.33f, h.x - sw, topY + (yEnd - topY) * 0.66f, h.x + sw * 0.5f, yEnd)
                }
                drawPath(p, CYAN.copy(h.a * 0.35f), style = Stroke(0.5f))
                var k = 0.1f
                while (k < 1f) {
                    val tw = (sin(tt * 3f + h.ph + k * 9f) + 1f) / 2f
                    drawCircle(HICE.copy(0.25f + 0.55f * tw), 0.35f,
                        Offset(bz(h.x, h.x + sw, h.x - sw, h.x + sw * 0.5f, k), bz(topY, topY + (yEnd - topY) * 0.33f, topY + (yEnd - topY) * 0.66f, yEnd, k)))
                    k += 0.09f
                }
            }

            // back hair
            fun strand(s: Strand) {
                val sw = sin(tt * 0.7f + s.ph) * 2.5f
                val p = Path().apply {
                    moveTo(s.x0, s.y0)
                    cubicTo(s.c1x + sw, s.c1y, s.c2x + sw * 1.6f, s.c2y, s.x1 + sw * 2f, s.y1)
                }
                val col = lerp(CYAN, HICE, (s.ph / 6.28f))
                drawPath(p, col.copy(s.a * 0.35f), style = Stroke(s.w * 2.4f))
                drawPath(p, col.copy(s.a), style = Stroke(s.w * 0.6f))
                val k = (tt * 0.12f + s.ph) % 1f
                drawCircle(HICE.copy(0.9f), 0.5f,
                    Offset(bz(s.x0, s.c1x + sw, s.c2x + sw * 1.6f, s.x1 + sw * 2f, k), bz(s.y0, s.c1y, s.c2y, s.y1, k)))
            }
            hair.filter { !it.front }.forEach { strand(it) }

            // shoulders + neck
            drawPath(shoulders, Brush.verticalGradient(listOf(Color(0xFF7F9FE0).copy(0.8f), Color(0xFF1B3A7A).copy(0.9f)), 88f, 130f))
            drawPath(shoulders, CYAN.copy(0.8f), style = Stroke(0.5f))
            drawPath(neck, Brush.verticalGradient(listOf(Color(0xFF8FA8E8).copy(0.9f), Color(0xFF3A5FA8).copy(0.9f)), 62f, 94f))
            drawPath(neck, Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Transparent), 62f, 76f))
            BODY_C.forEach { circuit(it, false, glow); circuit(it, true, glow) }

            // ears
            drawOval(Color(0xFFB8C8F0).copy(0.9f), Offset(29.5f, 36f), Size(4.5f, 9f))
            drawOval(Color(0xFFB8C8F0).copy(0.9f), Offset(66f, 36f), Size(4.5f, 9f))

            // head
            drawPath(head, Brush.verticalGradient(
                listOf(Color(0xFFE9F0FF).copy(0.95f), Color(0xFFB4C6F0).copy(0.95f), Color(0xFF6A88CC).copy(0.95f)), 10f, 72f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.5f), Color.Transparent), Offset(50f, 24f), 18f), 18f, Offset(50f, 24f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9FB0).copy(0.28f), Color.Transparent), Offset(39f, 52f), 7f), 7f, Offset(39f, 52f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9FB0).copy(0.28f), Color.Transparent), Offset(61f, 52f), 7f), 7f, Offset(61f, 52f))
            drawPath(head, Brush.horizontalGradient(
                listOf(Color(0xFF1B3A7A).copy(0.45f), Color.Transparent, Color.Transparent, Color(0xFF1B3A7A).copy(0.45f)), 32f, 68f))

            // forehead / cheek circuit traces
            FACE_C.forEach { circuit(it, false, glow); circuit(it, true, glow) }

            // brows
            for (m in 0..1) {
                val d = if (m == 0) 1f else -1f
                val cx = if (m == 0) 41f else 59f
                val b = Path().apply {
                    moveTo(cx - 7f * d, 35.5f)
                    quadraticBezierTo(cx - 1f * d, 31.8f, cx + 6f * d, 33.2f)
                }
                drawPath(b, CYAN.copy(0.35f), style = Stroke(2.6f))
                drawPath(b, HICE, style = Stroke(0.7f))
            }

            // eyes (blink)
            val blink = if ((tt % 4.5f) < 0.14f) 0.12f else 1f
            for (m in 0..1) {
                val cx = if (m == 0) 41f else 59f
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.9f), CYAN.copy(0.45f), Color.Transparent), Offset(cx, 39f), 9f), 9f, Offset(cx, 39f))
                val e = Path().apply {
                    moveTo(cx - 5.5f, 39f)
                    quadraticBezierTo(cx, 39f - 4.4f * blink, cx + 5.5f, 39f)
                    quadraticBezierTo(cx, 39f + 3.4f * blink, cx - 5.5f, 39f)
                    close()
                }
                drawPath(e, Color.White.copy(0.97f))
                drawPath(e, CYAN, style = Stroke(0.5f))
                drawCircle(CYAN.copy(0.6f), 1.8f * blink, Offset(cx, 39f))
            }

            // nose
            val nose = Path().apply {
                moveTo(48.5f, 40f); cubicTo(48f, 46f, 47f, 50f, 46.2f, 53f)
                quadraticBezierTo(50f, 56f, 53.8f, 53f)
                cubicTo(53f, 50f, 52f, 46f, 51.5f, 40f)
            }
            drawPath(nose, Color(0xFF203A7A).copy(0.4f), style = Stroke(0.5f))
            drawOval(Color(0xFF203A90).copy(0.35f), Offset(46.8f, 52.5f), Size(2.2f, 1.4f))
            drawOval(Color(0xFF203A90).copy(0.35f), Offset(51f, 52.5f), Size(2.2f, 1.4f))
            drawCircle(Color.White.copy(0.55f), 1.1f, Offset(50f, 51.5f))

            // lips (+ open when speaking)
            val open = if (speaking) 3f * pulse else 0f
            val upper = Path().apply {
                moveTo(43f, 60f); cubicTo(45.5f, 58.2f, 48f, 58.2f, 50f, 59.2f)
                cubicTo(52f, 58.2f, 54.5f, 58.2f, 57f, 60f)
                cubicTo(54f, 60.8f, 46f, 60.8f, 43f, 60f); close()
            }
            val lower = Path().apply {
                moveTo(43f, 60f); quadraticBezierTo(50f, 64.8f + open, 57f, 60f)
                cubicTo(54f, 60.8f, 46f, 60.8f, 43f, 60f); close()
            }
            drawPath(lower, Color(0xFF9B5C82))
            if (open > 0.3f) drawOval(Color(0xFF2A1030), Offset(45.5f, 60.3f), Size(9f, open * 0.9f))
            drawPath(upper, Color(0xFF6E3A5E))
            drawOval(Color.White.copy(0.35f), Offset(47f, 62f + open * 0.4f), Size(6f, 0.9f))

            // front hair strands over the shoulders
            hair.filter { it.front }.forEach { strand(it) }

            // rim light
            drawPath(head, CYAN.copy(0.25f), style = Stroke(2.6f))
            drawPath(head, CYAN.copy(0.9f), style = Stroke(0.6f))

            // particles
            for (i in 0 until 30) {
                val px = (i * 37 % 100).toFloat()
                val py = topY + 130f - topY - ((tt * (6f + (i % 5) * 3f) + i * 17f) % (130f - topY))
                drawCircle(HICE.copy(0.55f), 0.45f, Offset(px + sin(tt + i) * 2f, py))
            }
            // scanlines + sweep
            var y = topY
            while (y < 130f) { drawLine(Color.Black.copy(0.14f), Offset(0f, y), Offset(100f, y), 0.5f); y += 2.5f }
            val sweep = topY + (tt * 30f) % (130f - topY)
            drawRect(CYAN.copy(0.14f), Offset(0f, sweep), Size(100f, 2.5f))
        }
    }
}

// ───────────────────────── Platform (like reference) ─────────────────────────

@Composable
fun Platform(t: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val cx = size.width / 2f
        val cy = size.height * 0.62f
        val w0 = size.width * 0.46f
        drawOval(BLUE.copy(0.16f), Offset(cx - w0, cy - w0 * 0.2f), Size(w0 * 2f, w0 * 0.4f))
        for (i in 0..3) {
            val rw = w0 - i * size.width * 0.07f
            val rh = rw * 0.22f
            drawOval(BLUE.copy(0.2f + 0.12f * i), Offset(cx - rw, cy - rh), Size(rw * 2f, rh * 2f), style = Stroke(1.5f + i))
        }
        drawOval(CYAN.copy(0.9f), Offset(cx - w0 * 0.55f, cy - w0 * 0.12f), Size(w0 * 1.1f, w0 * 0.24f), style = Stroke(2f))
        for (i in 0 until 16) {
            val a = i * 0.4f + tt * 0.8f
            val x = cx + cos(a) * w0 * 0.8f
            val y = cy + sin(a) * w0 * 0.17f
            drawLine(CYAN.copy(0.5f), Offset(x, y), Offset(x, y - 18f - 10f * sin(tt * 3f + i)), 1.5f)
        }
    }
}

// ───────────────────────── HUD panels (reference image 1) ─────────────────────────

@Composable
fun HudPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.chamfer(10f, PURPLE).padding(6.dp), content = content)
}

@Composable
fun DnaPanel(t: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val n = 28
        val p1 = Path(); val p2 = Path()
        for (i in 0..n) {
            val y = size.height * i / n
            val a = i * 0.5f + tt * 2f
            val x1 = size.width / 2f + sin(a) * size.width * 0.38f
            val x2 = size.width / 2f - sin(a) * size.width * 0.38f
            if (i == 0) { p1.moveTo(x1, y); p2.moveTo(x2, y) } else { p1.lineTo(x1, y); p2.lineTo(x2, y) }
            if (i % 2 == 0) drawLine(Color.White.copy(0.35f), Offset(x1, y), Offset(x2, y), 1f)
        }
        drawPath(p1, PURPLE, style = Stroke(3f))
        drawPath(p2, Color(0xFFB8A8FF), style = Stroke(3f))
    }
}

@Composable
fun BarsPanel(t: State<Float>, amp: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val n = 6
        val bw = size.width / (n * 1.6f)
        for (i in 0 until n) {
            val h = size.height * min(0.95f, 0.2f + 0.75f * abs(sin(tt * 1.3f + i * 0.9f)) * (0.35f + amp * 0.65f))
            val x = i * bw * 1.6f + bw * 0.3f
            drawRect(if (i % 2 == 0) PURPLE else CYAN, Offset(x, size.height - h), Size(bw, h))
        }
    }
}

@Composable
fun OrangeLinePanel(t: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        for (i in 1..3) drawLine(Color.White.copy(0.08f), Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1f)
        val p = Path()
        val n = 24
        for (i in 0..n) {
            val x = size.width * i / n
            val y = size.height * (0.45f + 0.28f * sin(i * 0.5f + tt * 0.9f) + 0.12f * sin(i * 1.3f - tt * 1.4f))
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, ORANGE.copy(0.3f), style = Stroke(7f))
        drawPath(p, ORANGE, style = Stroke(3f))
        val cols = listOf(GREEN, BLUE, PINK)
        cols.forEachIndexed { i, c -> drawCircle(c, 4f, Offset(8f + i * 16f, size.height - 6f)) }
    }
}

@Composable
fun EcgPanel(t: State<Float>, amp: Float, modifier: Modifier = Modifier) {
    val bpm = (68 + 70 * amp).toInt()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(top = 16.dp)) {
            val tt = t.value
            val p = Path()
            val n = 60
            for (i in 0..n) {
                val x = size.width * i / n
                val ph = (i * 0.045f + tt * 0.9f) % 1f
                val y = size.height / 2f + when {
                    ph < 0.05f -> -size.height * 0.1f
                    ph < 0.09f -> size.height * 0.42f
                    ph < 0.13f -> -size.height * 0.45f
                    else -> sin(i * 2f + tt * 4f) * size.height * 0.03f
                }
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, PINK.copy(0.3f), style = Stroke(6f))
            drawPath(p, PINK, style = Stroke(2.2f))
        }
        Text("♥ $bpm", color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun WavePanel(t: State<Float>, amp: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val mid = size.height / 2f
        var x = 0f
        var i = 0
        while (x < size.width) {
            val v = sin(i * 0.4f + tt * 9f) * 0.5f + sin(i * 1.3f - tt * 13f) * 0.35f + sin(i * 2.9f + tt * 5f) * 0.15f
            val a = abs(v) * size.height * 0.45f * (0.15f + amp)
            drawLine(GREEN.copy(0.85f), Offset(x, mid - a), Offset(x, mid + a), 2f)
            x += 4f; i++
        }
    }
}

@Composable
fun GaugePanel(t: State<Float>, amp: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tt = t.value
        val d = min(size.width * 0.9f, size.height * 1.6f)
        val tl = Offset((size.width - d) / 2f, size.height * 0.1f)
        val v = (0.2f + 0.75f * amp * (0.6f + 0.4f * abs(sin(tt * 0.7f)))).coerceIn(0f, 1f)
        drawArc(PURPLE.copy(0.3f), 180f, 180f, false, tl, Size(d, d), style = Stroke(10f))
        drawArc(CYAN, 180f, 180f * v, false, tl, Size(d, d), style = Stroke(10f))
        val c = Offset(tl.x + d / 2f, tl.y + d / 2f)
        val a = (PI * (1.0 + v)).toFloat()
        drawLine(Color.White, c, Offset(c.x + cos(a) * d * 0.4f, c.y + sin(a) * d * 0.4f), 2f)
        drawCircle(Color.White, 4f, c)
        drawRect(PURPLE.copy(0.5f), Offset(4f, size.height - 10f), Size(size.width - 8f, 5f))
        drawRect(CYAN, Offset(4f, size.height - 10f), Size((size.width - 8f) * v, 5f))
    }
}
