package com.antonio.labinestable

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

fun DrawScope.drawGame(g: Game) {
    val s = min(size.width / Game.W, size.height / Game.H)
    val ox = (size.width - Game.W * s) / 2f
    val oy = (size.height - Game.H * s) / 2f
    drawRect(Color(0xFF080B0F))

    val shk = g.shake * s
    val sx = if (shk > 0f) (Random.nextFloat() - 0.5f) * shk else 0f
    val sy = if (shk > 0f) (Random.nextFloat() - 0.5f) * shk else 0f
    withTransform({
        translate(ox + sx, oy + sy)
        scale(s, s, Offset.Zero)
    }) {
        drawWorld(g)
    }

    if (g.joyOn) {
        val dx = g.joyX - g.joyOx
        val dy = g.joyY - g.joyOy
        val d = hypot(dx, dy)
        val k = if (d > 56f) 56f / d else 1f
        drawCircle(Color(0x22FFFFFF), 56f, Offset(g.joyOx, g.joyOy))
        drawCircle(Color(0x55FFFFFF), 22f, Offset(g.joyOx + dx * k, g.joyOy + dy * k))
    }
    if (g.flash > 0f) drawRect(Color.White.copy(alpha = g.flash.coerceIn(0f, 1f)))
}

private fun DrawScope.drawWorld(g: Game) {
    val t = g.anim
    val f = (g.inst / 100f).coerceIn(0f, 1f)
    val rc = Offset(Game.RX, Game.RY)

    clipRect(0f, 0f, Game.W, Game.H) {
        // suelo de baldosas
        drawRect(Color(0xFF18232E), Offset.Zero, Size(Game.W, Game.H))
        for (i in 0 until 9) for (j in 0 until 16) {
            if ((i + j) % 2 == 0) drawRect(Color(0x0DFFFFFF), Offset(i * 40f, j * 40f), Size(40f, 40f))
        }
        // pared superior con franja de peligro
        drawRect(Color(0xFF0F161D), Offset.Zero, Size(Game.W, 60f))
        var x = 0f
        var k = 0
        while (x < Game.W) {
            drawRect(if (k % 2 == 0) Color(0xFFF2B632) else Color(0xFF1A1A1A), Offset(x, 56f), Size(20f, 4f))
            x += 20f
            k++
        }
        // mesas con frascos
        drawRoundRect(Color(0xFF2B3A48), Offset(10f, 70f), Size(70f, 24f), CornerRadius(6f))
        drawCircle(Color(0xFF63E6D4), 6f, Offset(26f, 68f))
        drawCircle(Color(0xFFFF6FB5), 5f, Offset(46f, 69f))
        drawCircle(Color(0xFFB8F2E6), 4f, Offset(64f, 70f))
        drawRoundRect(Color(0xFF2B3A48), Offset(280f, 70f), Size(70f, 24f), CornerRadius(6f))
        drawCircle(Color(0xFFFFE14D), 6f, Offset(298f, 68f))
        drawCircle(Color(0xFF9B5DE5), 5f, Offset(318f, 69f))
        drawCircle(Color(0xFF63E6D4), 4f, Offset(336f, 70f))
        // desagües y marcas del suelo
        drawCircle(Color(0x33000000), 16f, Offset(60f, 590f))
        drawCircle(Color(0x33000000), 16f, Offset(300f, 590f))
        drawCircle(
            Color(0x44F2B632), Game.RR + 70f, rc,
            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        )
        // tuberías hacia el reactor
        drawRect(Color(0xFF2E3D4C), Offset(0f, Game.RY - 6f), Size(Game.RX - Game.RR - 8f, 12f))
        drawRect(Color(0xFF3C4F61), Offset(0f, Game.RY - 6f), Size(Game.RX - Game.RR - 8f, 4f))
        drawRect(Color(0xFF2E3D4C), Offset(Game.RX + Game.RR + 8f, Game.RY - 6f), Size(Game.W, 12f))
        drawRect(Color(0xFF3C4F61), Offset(Game.RX + Game.RR + 8f, Game.RY - 6f), Size(Game.W, 4f))

        // reactor
        val pulse = 1f + 0.06f * sin(t * (3f + f * 10f))
        val col = lerp(Color(0xFF3DDC84), Color(0xFFFF3B30), f)
        drawCircle(col.copy(alpha = 0.12f + 0.2f * f), Game.RR * 2.2f * pulse, rc)
        drawCircle(Color(0xFF2B3948), Game.RR + 10f, rc)
        drawCircle(col, Game.RR + 10f, rc, style = Stroke(4f))
        drawCircle(col, Game.RR * 0.75f * pulse, rc)
        drawCircle(Color.White.copy(alpha = 0.45f), Game.RR * 0.25f * pulse, Offset(Game.RX - 9f, Game.RY - 9f))
        if (f > 0.6f) {
            val cr = Color(0xFF111111)
            drawLine(cr, Offset(Game.RX - 4f, Game.RY - 4f), Offset(Game.RX - 24f, Game.RY - 28f), 2.5f)
            drawLine(cr, Offset(Game.RX + 6f, Game.RY + 2f), Offset(Game.RX + 26f, Game.RY + 22f), 2.5f)
            drawLine(cr, Offset(Game.RX + 2f, Game.RY - 6f), Offset(Game.RX + 14f, Game.RY - 30f), 2.5f)
        }
        if (g.reactorHit > 0f) drawCircle(Color.White.copy(alpha = 0.6f), Game.RR + 10f, rc)

        // aura tóxica
        if (g.auraLv > 0) {
            drawCircle(Color(0xFF7CFC00).copy(alpha = 0.10f), 60f, Offset(g.px, g.py))
            drawCircle(Color(0xFF7CFC00).copy(alpha = 0.35f), 60f, Offset(g.px, g.py), style = Stroke(2f))
        }

        // ideas
        for (o in g.orbs) {
            drawCircle(Color(0x44FFE14D), 9f, Offset(o.x, o.y))
            drawCircle(Color(0xFFFFE14D), 4.5f, Offset(o.x, o.y))
        }

        // mutantes
        for (m in g.mutants) {
            val col2 = when (m.kind) {
                2 -> Color(0xFF9B5DE5)
                1 -> Color(0xFFFF9F1C)
                else -> Color(0xFF6BCB3A)
            }
            val r = m.r * (1f + 0.06f * sin(t * 8f + m.x * 0.1f))
            drawOval(Color(0x44000000), Offset(m.x - r, m.y + r * 0.6f), Size(r * 2f, r * 0.7f))
            drawCircle(col2, r, Offset(m.x, m.y))
            if (m.hit > 0f) drawCircle(Color.White.copy(alpha = 0.7f), r, Offset(m.x, m.y))
            val ddx = Game.RX - m.x
            val ddy = Game.RY - m.y
            val dd = max(1f, hypot(ddx, ddy))
            for (sgn in intArrayOf(-1, 1)) {
                val cx = m.x + sgn * r * 0.38f
                val cy = m.y - r * 0.15f
                drawCircle(Color.White, r * 0.28f, Offset(cx, cy))
                drawCircle(Color(0xFF111111), r * 0.13f, Offset(cx + ddx / dd * r * 0.1f, cy + ddy / dd * r * 0.1f))
            }
            if (m.kind == 2 && m.hp < m.maxHp) {
                drawRect(Color(0x88000000), Offset(m.x - 14f, m.y - r - 8f), Size(28f, 4f))
                drawRect(Color(0xFFFF5A36), Offset(m.x - 14f, m.y - r - 8f), Size(28f * (m.hp / m.maxHp).coerceIn(0f, 1f), 4f))
            }
        }

        // frascos
        for (v in g.vials) {
            rotate(v.rot * 57.3f, Offset(v.x, v.y)) {
                drawRect(Color(0xFFDDEEF2), Offset(v.x - 1.5f, v.y - 9f), Size(3f, 5f))
                drawCircle(Color(0xFF63E6D4), 4.5f, Offset(v.x, v.y))
            }
        }

        // científico
        val blink = g.inv > 0f && ((t * 20f).toInt() % 2 == 0)
        if (!blink) {
            val px = g.px
            val py = g.py
            drawOval(Color(0x55000000), Offset(px - 11f, py + 8f), Size(22f, 8f))
            drawRoundRect(Color.White, Offset(px - 9f, py - 8f), Size(18f, 20f), CornerRadius(7f))
            drawRect(Color(0xFFC9D3DC), Offset(px - 0.7f, py - 6f), Size(1.4f, 17f))
            drawCircle(Color(0xFFF1C27D), 7f, Offset(px, py - 13f))
            drawArc(
                Color(0xFF3B2F2F), 180f, 180f, true,
                Offset(px - 7.5f, py - 21f), Size(15f, 15f)
            )
            drawRect(Color(0xFF56E0E0), Offset(px - 6.5f, py - 14.5f), Size(13f, 4f))
            drawRect(Color(0xFF1B2A33), Offset(px - 6.5f, py - 13f), Size(13f, 1f))
        }

        // onda de choque
        if (g.novaAnim > 0f) {
            val p = 1f - g.novaAnim / 0.4f
            drawCircle(
                Color(0xFF7DF9FF).copy(alpha = (1f - p).coerceIn(0f, 1f)),
                g.novaRad * p, Offset(g.px, g.py), style = Stroke(4f)
            )
        }

        // partículas
        for (p in g.parts) {
            drawCircle(p.color.copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)), p.size, Offset(p.x, p.y))
        }

        // bola de fuego de la explosión
        if (g.phase == Phase.EXPLODING) {
            val bl = g.explT * 190f
            drawCircle(Color(0xFFFF5A36).copy(alpha = 0.8f), Game.RR + bl, rc)
            drawCircle(Color(0xFFFFB347).copy(alpha = 0.9f), (Game.RR + bl) * 0.75f, rc)
            drawCircle(Color.White.copy(alpha = 0.9f), (Game.RR + bl) * 0.4f, rc)
        }
    }
}
