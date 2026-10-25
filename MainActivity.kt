package com.antonio.labinestable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = Game(Store(applicationContext))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(game)
            }
        }
    }
}

@Composable
fun App(game: Game) {
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else (t - last) / 1_000_000_000f
                last = t
                game.update(dt)
                game.tick++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080B0F))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            game.joyOn = true
                            game.joyOx = o.x
                            game.joyOy = o.y
                            game.joyX = o.x
                            game.joyY = o.y
                        },
                        onDragEnd = { game.stopJoy() },
                        onDragCancel = { game.stopJoy() },
                        onDrag = { change, _ ->
                            change.consume()
                            game.joyX = change.position.x
                            game.joyY = change.position.y
                            game.joyUpdate()
                        }
                    )
                }
        ) {
            game.tick
            drawGame(game)
        }

        if (game.phase != Phase.MENU) Hud(game)
        when (game.phase) {
            Phase.MENU -> Menu(game)
            Phase.LEVELUP -> LevelUp(game)
            else -> {}
        }
    }
}

@Composable
fun Bar(f: Float, c: Color, h: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x66000000))
    ) {
        Box(
            Modifier
                .fillMaxWidth(f.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(c)
        )
    }
}

@Composable
fun Hud(g: Game) {
    g.tick
    val f = (g.inst / 100f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${g.time.toInt()} s", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Nivel ${g.level}", color = Color(0xFFFFE14D), fontWeight = FontWeight.Bold)
            Text("${g.kills} mutantes", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Bar(f, lerp(Color(0xFF3DDC84), Color(0xFFFF3B30), f), 12.dp)
        Text("Inestabilidad ${g.inst.toInt()}%", color = Color(0xFFCBD5DD), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Bar(g.xp / g.xpNeed, Color(0xFFFFE14D), 5.dp)
    }
}

@Composable
fun LevelUp(g: Game) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC000000)).padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("¡Eureka! Nivel ${g.level}", color = Color(0xFFFFE14D), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Elige una mejora para esta partida", color = Color.White)
        Spacer(Modifier.height(12.dp))
        g.choices.forEach { p ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2A36))
                    .clickable { g.choose(p) }
                    .padding(16.dp)
            ) {
                Text(p.name, color = Color(0xFF63E6D4), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(p.desc, color = Color(0xFFCBD5DD), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun Menu(g: Game) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE080B0F))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Laboratorio Inestable", color = Color(0xFF63E6D4), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Arrastra el dedo para moverte. Disparas solo. Defiende el reactor de los mutantes: si la inestabilidad llega a 100%, el laboratorio explota.",
            color = Color(0xFF9AA7B4), fontSize = 13.sp
        )
        if (g.lastMsg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(g.lastMsg, color = Color(0xFFFFB347), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("Eureka: ${g.eureka}   ·   Mejor tiempo: ${g.best} s", color = Color.White)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { g.start() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Entrar al laboratorio", fontSize = 18.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Mejoras permanentes", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        META.forEachIndexed { i, m ->
            val lv = g.metaLv[i]
            val maxed = lv >= MAXL
            val c = g.metaCost(i)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2A36))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${m.name}  nivel $lv", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(m.desc, color = Color(0xFF9AA7B4), fontSize = 13.sp)
                }
                Button(onClick = { g.buy(i) }, enabled = !maxed && g.eureka >= c) {
                    Text(if (maxed) "Máx" else "$c")
                }
            }
        }
    }
}
