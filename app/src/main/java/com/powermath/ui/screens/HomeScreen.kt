package com.powermath.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.R
import com.powermath.math.Examples
import com.powermath.ui.components.Chip
import com.powermath.ui.theme.LocalAnimations
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen
import kotlinx.coroutines.delay

private class HomeTile(val symbol: String, val title: String, val subtitle: String, val screen: Screen)

private val tiles = listOf(
    HomeTile("÷×", "Calculator", "Type anything: numbers, equations, inequalities", Screen.CALCULATOR),
    HomeTile("ƒ(x)", "Graph", "Plot functions, zoom and pan", Screen.GRAPH),
    HomeTile("∑", "Tools", "Calculus, matrices, units, primes, shapes, unit circle", Screen.TOOLS),
    HomeTile("x=", "Workspace", "Saved variables and functions", Screen.WORKSPACE),
    HomeTile("↺", "History", "Reopen earlier calculations", Screen.HISTORY),
    HomeTile("⚙", "Settings", "Colours, keys, animations", Screen.SETTINGS)
)

/** Fades and slides a block in after [index] * 70 ms (immediately when animations are turned off). */
@Composable
private fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val animate = LocalAnimations.current
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { if (animate) { delay(70L * index); shown = true } }
    AnimatedVisibility(shown, modifier = modifier, enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 4 }) { content() }
}

@Composable
private fun TileCard(tile: HomeTile, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "${tile.title}. ${tile.subtitle}" },
        shape = RoundedCornerShape(18.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Text(tile.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer, maxLines = 1)
            }
            Text(tile.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(tile.subtitle, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The start page: every part of the app is one tap away, plus one-tap examples. */
@Composable
fun HomeScreen(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    var category by rememberSaveable { mutableStateOf(Examples.categories.first()) }
    val recent by vm.history.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Reveal(0) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_powermath), contentDescription = "MathLab logo", modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("MathLab", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Exact math. Fully offline.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Chip("Scan", { vm.scan = com.powermath.viewmodel.ScanState.Choose }, desc = "Scan an expression from a photo")
                Spacer(Modifier.size(6.dp))
                Chip("Settings", { vm.screen = Screen.SETTINGS }, desc = "Open settings")
            }
        }

        Reveal(1) {
            Surface(
                onClick = { vm.screen = Screen.CALCULATOR },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { contentDescription = "Open the calculator and start typing" },
                shape = RoundedCornerShape(18.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Type anything", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("2x + 3 = 11   ·   4 < 2 + x < 6   ·   or scan a photo", fontSize = 12.sp, maxLines = 2)
                    }
                    Text("→", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- tiles in two columns ----
        tiles.chunked(2).forEachIndexed { r, pair ->
            Reveal(2 + r) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in pair) TileCard(t, Modifier.weight(1f)) { if (t.screen == Screen.GRAPH) vm.openGraph(null) else vm.screen = t.screen }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---- recent calculations ----
        if (recent.isNotEmpty()) {
            Reveal(5) {
                Column {
                    Text("Recent", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.primary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (h in recent.take(8)) Chip(h.expression.take(28), { vm.restoreFromHistory(h) }, desc = "Reopen ${h.expression}")
                    }
                }
            }
        }

        // ---- examples ----
        Reveal(6) {
            Column {
                Text("Try an example", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.primary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in Examples.categories) Chip(c, { category = c }, active = c == category, desc = "$c examples")
                }
                Spacer(Modifier.height(8.dp))
                for (ex in Examples.of(category)) {
                    Surface(
                        onClick = { vm.runInput(ex.input) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics { contentDescription = "Run example ${ex.title}: ${ex.input}" },
                        shape = RoundedCornerShape(14.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.3f))
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.title, fontSize = 12.sp, color = cs.onSurfaceVariant)
                                Text(ex.input, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("Run", fontSize = 13.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Reveal(7) {
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Powered by ", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text("CloudPixel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
            }
        }
    }
}
