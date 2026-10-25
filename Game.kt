package com.antonio.labinestable

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class Phase { MENU, PLAYING, LEVELUP, EXPLODING }

class Mutant(var x: Float, var y: Float, var hp: Float, val maxHp: Float, val speed: Float, val kind: Int) {
    var hit = 0f
    var kx = 0f
    var ky = 0f
    val r: Float get() = when (kind) { 2 -> 17f; 1 -> 9f; else -> 12f }
}

class Vial(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val dmg: Float, var pierce: Int) {
    val hitSet = HashSet<Mutant>()
    var rot = 0f
}

class Orb(var x: Float, var y: Float)

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Color, val size: Float
)

class Perk(val name: String, val desc: String, val apply: (Game) -> Unit)

class MetaUp(val name: String, val desc: String, val base: Int)

val META = listOf(
    MetaUp("Vial potente", "+10% de daño por nivel", 20),
    MetaUp("Manos rápidas", "+6% de cadencia por nivel", 20),
    MetaUp("Zapatillas antiestáticas", "+5% de velocidad por nivel", 15),
    MetaUp("Estabilizador", "−6% de inestabilidad ganada por nivel", 30),
    MetaUp("Imán de ideas", "+12 de alcance para recoger por nivel", 15),
    MetaUp("Beca de investigación", "+10% de Eureka al explotar por nivel", 40)
)
const val MAXL = 10

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lab_inestable", Context.MODE_PRIVATE)
    fun getInt(k: String, d: Int): Int = sp.getInt(k, d)
    fun putInt(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
}

class Game(private val store: Store) {
    companion object {
        const val W = 360f
        const val H = 640f
        const val RX = 180f
        const val RY = 320f
        const val RR = 34f
    }

    // ---- estado observable ----
    var phase by mutableStateOf(Phase.MENU)
    var tick by mutableIntStateOf(0)
    var eureka by mutableIntStateOf(store.getInt("e", 0))
    var best by mutableIntStateOf(store.getInt("best", 0))
    var lastMsg by mutableStateOf("")
    var choices by mutableStateOf(listOf<Perk>())
    val metaLv = mutableStateListOf<Int>().also { l -> repeat(META.size) { i -> l.add(store.getInt("l$i", 0)) } }

    // ---- estado de la partida ----
    var anim = 0f
    var px = 180f
    var py = 470f
    var dirX = 0f
    var dirY = 0f
    var inst = 0f
    var time = 0f
    var kills = 0
    var level = 1
    var xp = 0f
    var xpNeed = 5f
    var dmg = 2f
    var fireCd = 0.55f
    var fireT = 0f
    var shots = 1
    var pierce = 0
    var speed = 110f
    var magnet = 50f
    var xpMul = 1f
    var instMul = 1f
    var novaLv = 0
    var novaT = 0f
    var novaAnim = 0f
    var novaRad = 0f
    var auraLv = 0
    var inv = 0f
    var spawnT = 0f
    var explT = 0f
    var flash = 0f
    var shake = 0f
    var reactorHit = 0f

    // joystick (coordenadas de pantalla)
    var joyOn = false
    var joyOx = 0f
    var joyOy = 0f
    var joyX = 0f
    var joyY = 0f

    val mutants = ArrayList<Mutant>()
    val vials = ArrayList<Vial>()
    val orbs = ArrayList<Orb>()
    val parts = ArrayList<Particle>()

    private val boomCols = listOf(Color(0xFFFFB347), Color(0xFFFF5A36), Color(0xFFFFE14D), Color.White)

    val perks = listOf(
        Perk("Frasco doble", "Lanzas un frasco más por disparo") { it.shots++ },
        Perk("Ácido corrosivo", "+30% de daño") { it.dmg *= 1.3f },
        Perk("Centrifugadora", "+25% de cadencia") { it.fireCd *= 0.8f },
        Perk("Frasco perforante", "Los frascos atraviesan un mutante más") { it.pierce++ },
        Perk("Refrigerante", "Baja la inestabilidad en 25 puntos ahora") { it.inst = max(0f, it.inst - 25f) },
        Perk("Botas de goma", "+15% de velocidad") { it.speed *= 1.15f },
        Perk("Onda de choque", "Pulso que daña y empuja cada 5 s") { it.novaLv++ },
        Perk("Aura tóxica", "Daña sin parar a los mutantes cercanos") { it.auraLv++ },
        Perk("Imán de ideas", "+40 de alcance para recoger") { it.magnet += 40f },
        Perk("Café doble", "+30% de experiencia") { it.xpMul *= 1.3f },
        Perk("Válvula de alivio", "−15% de inestabilidad ganada") { it.instMul *= 0.85f }
    )

    // ---- meta ----
    fun metaCost(i: Int): Int = (META[i].base * 1.6.pow(metaLv[i].toDouble())).roundToInt()

    fun buy(i: Int) {
        val c = metaCost(i)
        if (metaLv[i] < MAXL && eureka >= c) {
            eureka -= c
            metaLv[i] = metaLv[i] + 1
            store.putInt("e", eureka)
            store.putInt("l$i", metaLv[i])
        }
    }

    // ---- control ----
    fun joyUpdate() {
        val dx = joyX - joyOx
        val dy = joyY - joyOy
        val d = hypot(dx, dy)
        if (d > 110f) {
            joyOx += dx / d * (d - 110f)
            joyOy += dy / d * (d - 110f)
        }
        val ex = joyX - joyOx
        val ey = joyY - joyOy
        val dd = hypot(ex, ey)
        if (dd > 8f) {
            val k = min(1f, dd / 60f)
            dirX = ex / dd * k
            dirY = ey / dd * k
        } else {
            dirX = 0f
            dirY = 0f
        }
    }

    fun stopJoy() {
        joyOn = false
        dirX = 0f
        dirY = 0f
    }

    fun start() {
        val m = metaLv
        px = 180f; py = 470f; dirX = 0f; dirY = 0f
        inst = 0f; time = 0f; kills = 0; level = 1; xp = 0f; xpNeed = 5f
        dmg = 2f * (1f + 0.10f * m[0])
        fireCd = 0.55f / (1f + 0.06f * m[1])
        fireT = 0f; shots = 1; pierce = 0
        speed = 110f * (1f + 0.05f * m[2])
        instMul = max(0.3f, 1f - 0.06f * m[3])
        magnet = 50f + 12f * m[4]
        xpMul = 1f; novaLv = 0; novaT = 0f; novaAnim = 0f; auraLv = 0
        inv = 0f; spawnT = 1.2f; explT = 0f; flash = 0f; shake = 0f; reactorHit = 0f
        mutants.clear(); vials.clear(); orbs.clear(); parts.clear()
        phase = Phase.PLAYING
    }

    fun choose(p: Perk) {
        p.apply(this)
        inv = 1f
        phase = Phase.PLAYING
    }

    private fun openLevelUp() {
        choices = perks.shuffled().take(3)
        dirX = 0f
        dirY = 0f
        phase = Phase.LEVELUP
    }

    // ---- efectos ----
    private fun burst(x: Float, y: Float, c: Color, n: Int) {
        if (parts.size > 450) return
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = 30f + Random.nextFloat() * 90f
            val l = 0.3f + Random.nextFloat() * 0.4f
            parts.add(Particle(x, y, cos(a) * s, sin(a) * s, l, l, c, 2f + Random.nextFloat() * 2f))
        }
    }

    private fun explode(n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = 60f + Random.nextFloat() * 240f
            val l = 0.6f + Random.nextFloat() * 0.9f
            parts.add(Particle(RX, RY, cos(a) * s, sin(a) * s, l, l, boomCols.random(), 3f + Random.nextFloat() * 5f))
        }
    }

    private fun stepParticles(dt: Float) {
        val i = parts.iterator()
        while (i.hasNext()) {
            val p = i.next()
            p.life -= dt
            if (p.life <= 0f) {
                i.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.97f
            p.vy *= 0.97f
        }
    }

    private fun spawnMutant() {
        val kind = when {
            time > 75f && Random.nextFloat() < 0.15f -> 2
            time > 30f && Random.nextFloat() < 0.3f -> 1
            else -> 0
        }
        val (x, y) = when (Random.nextInt(4)) {
            0 -> Pair(Random.nextFloat() * W, -15f)
            1 -> Pair(Random.nextFloat() * W, H + 15f)
            2 -> Pair(-15f, Random.nextFloat() * H)
            else -> Pair(W + 15f, Random.nextFloat() * H)
        }
        val scale = 1f + time / 90f
        val hp = when (kind) { 2 -> 14f; 1 -> 2f; else -> 3f } * scale
        val sp = when (kind) { 2 -> 28f; 1 -> 72f; else -> 42f }
        mutants.add(Mutant(x, y, hp, hp, sp, kind))
    }

    private fun kill(m: Mutant) {
        kills++
        orbs.add(Orb(m.x, m.y))
        val c = when (m.kind) { 2 -> Color(0xFF9B5DE5); 1 -> Color(0xFFFF9F1C); else -> Color(0xFF6BCB3A) }
        burst(m.x, m.y, c, 7)
    }

    // ---- bucle ----
    fun update(dt0: Float) {
        val dt = min(dt0, 0.05f)
        anim += dt
        if (novaAnim > 0f) novaAnim = max(0f, novaAnim - dt)
        stepParticles(dt)
        if (phase == Phase.EXPLODING) {
            stepExplosion(dt)
            return
        }
        if (phase != Phase.PLAYING) return

        time += dt
        inst += 0.3f * (1f + time / 150f) * instMul * dt
        px = (px + dirX * speed * dt).coerceIn(16f, W - 16f)
        py = (py + dirY * speed * dt).coerceIn(70f, H - 20f)
        inv = max(0f, inv - dt)
        reactorHit = max(0f, reactorHit - dt)
        shake = max(0f, shake - dt * 30f)

        // aparición de mutantes
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnMutant()
            if (time > 90f && Random.nextFloat() < 0.3f) spawnMutant()
            spawnT = max(0.3f, 1.6f - time / 100f) * (0.8f + Random.nextFloat() * 0.4f)
        }

        // onda de choque
        if (novaLv > 0) {
            novaT += dt
            if (novaT >= 5f) {
                novaT = 0f
                novaAnim = 0.4f
                novaRad = 70f + 15f * novaLv
                for (m in mutants) {
                    val dx = m.x - px
                    val dy = m.y - py
                    val d = max(1f, hypot(dx, dy))
                    if (d < novaRad) {
                        m.hp -= 3f * novaLv
                        m.hit = 0.15f
                        m.kx = dx / d * 220f
                        m.ky = dy / d * 220f
                    }
                }
            }
        }

        // mutantes
        val mi = mutants.iterator()
        while (mi.hasNext()) {
            val m = mi.next()
            m.hit = max(0f, m.hit - dt)
            val dx = RX - m.x
            val dy = RY - m.y
            val d = max(0.001f, hypot(dx, dy))
            if (d < RR + m.r * 0.6f) {
                inst += (if (m.kind == 2) 12f else 4f) * instMul
                reactorHit = 0.25f
                shake = max(shake, 6f)
                burst(m.x, m.y, Color(0xFF9EF01A), 8)
                mi.remove()
                continue
            }
            m.x += dx / d * m.speed * dt + m.kx * dt
            m.y += dy / d * m.speed * dt + m.ky * dt
            val decay = min(1f, 6f * dt)
            m.kx -= m.kx * decay
            m.ky -= m.ky * decay
            val pdx = px - m.x
            val pdy = py - m.y
            val pd2 = pdx * pdx + pdy * pdy
            if (inv <= 0f && pd2 < (m.r + 10f) * (m.r + 10f)) {
                inst += 3f * instMul
                inv = 0.9f
                shake = max(shake, 5f)
                val pd = max(1f, sqrt(pd2))
                m.kx = -pdx / pd * 160f
                m.ky = -pdy / pd * 160f
                burst(px, py, Color.White, 6)
            }
            if (auraLv > 0 && pd2 < 60f * 60f) {
                m.hp -= 4f * auraLv * dt
                m.hit = 0.08f
            }
            if (m.hp <= 0f) {
                kill(m)
                mi.remove()
            }
        }

        if (inst >= 100f) {
            startExplosion()
            return
        }

        // disparo automático
        fireT -= dt
        if (fireT <= 0f && mutants.isNotEmpty()) {
            var target: Mutant? = null
            var bd = Float.MAX_VALUE
            for (m in mutants) {
                val dx = m.x - px
                val dy = m.y - py
                val d2 = dx * dx + dy * dy
                if (d2 < bd) {
                    bd = d2
                    target = m
                }
            }
            val tg = target
            if (tg != null && bd < 260f * 260f) {
                fireT = fireCd
                val ang = atan2(tg.y - py, tg.x - px)
                for (i in 0 until shots) {
                    val a = ang + (i - (shots - 1) / 2f) * 0.22f
                    vials.add(Vial(px, py, cos(a) * 260f, sin(a) * 260f, 1.2f, dmg, pierce))
                }
            }
        }

        // frascos
        val vi = vials.iterator()
        while (vi.hasNext()) {
            val v = vi.next()
            v.x += v.vx * dt
            v.y += v.vy * dt
            v.life -= dt
            v.rot += dt * 12f
            if (v.life <= 0f || v.x < -20f || v.x > W + 20f || v.y < -20f || v.y > H + 20f) {
                vi.remove()
                continue
            }
            var gone = false
            for (m in mutants) {
                if (m in v.hitSet) continue
                val dx = m.x - v.x
                val dy = m.y - v.y
                val rr = m.r + 5f
                if (dx * dx + dy * dy < rr * rr) {
                    m.hp -= v.dmg
                    m.hit = 0.1f
                    v.hitSet.add(m)
                    burst(v.x, v.y, Color(0xFFB8F2E6), 3)
                    if (v.pierce <= 0) {
                        gone = true
                        break
                    } else {
                        v.pierce--
                    }
                }
            }
            if (gone) vi.remove()
        }

        // vapor del reactor
        if (inst > 50f && Random.nextFloat() < 0.2f) {
            parts.add(
                Particle(
                    RX + (Random.nextFloat() - 0.5f) * 30f, RY - RR,
                    (Random.nextFloat() - 0.5f) * 20f, -40f, 0.9f, 0.9f, Color(0x88FFFFFF), 4f
                )
            )
        }

        // ideas (experiencia)
        val oi = orbs.iterator()
        var levelUp = false
        while (oi.hasNext()) {
            val o = oi.next()
            val dx = px - o.x
            val dy = py - o.y
            val d = hypot(dx, dy)
            if (d < magnet && d > 1f) {
                o.x += dx / d * 200f * dt
                o.y += dy / d * 200f * dt
            }
            if (d < 14f) {
                oi.remove()
                xp += xpMul
                if (xp >= xpNeed) {
                    xp -= xpNeed
                    level++
                    xpNeed = 5f + level * 3f
                    levelUp = true
                }
            }
        }
        if (levelUp) openLevelUp()
    }

    // ---- explosión ----
    private fun startExplosion() {
        inst = 100f
        phase = Phase.EXPLODING
        explT = 0f
        dirX = 0f
        dirY = 0f
        joyOn = false
        val reward = ((time + kills * 0.5f + level * 5f) * (1f + 0.1f * metaLv[5])).roundToInt()
        eureka += reward
        val secs = time.toInt()
        if (secs > best) best = secs
        store.putInt("e", eureka)
        store.putInt("best", best)
        lastMsg = "El laboratorio explotó a los $secs s. $kills mutantes, nivel $level. +$reward Eureka"
    }

    private fun stepExplosion(dt: Float) {
        explT += dt
        shake = 16f * max(0f, 1f - explT / 2.8f)
        flash = if (explT < 1.3f) explT / 1.3f * 0.9f else max(0f, (2.4f - explT) / 1.1f)
        if (explT < 1.4f) explode(6)
        if (explT >= 2.8f) {
            flash = 0f
            shake = 0f
            phase = Phase.MENU
        }
    }
}
