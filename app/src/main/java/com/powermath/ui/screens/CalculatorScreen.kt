package com.powermath.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.editor.Pretty
import com.powermath.math.AngleMode
import com.powermath.math.FormatSettings
import com.powermath.math.ResultMode
import com.powermath.ui.components.Chip
import com.powermath.ui.components.KeyAction
import com.powermath.ui.components.KeyLayouts
import com.powermath.ui.components.Keypad
import com.powermath.ui.components.ModifierRow
import com.powermath.ui.components.Segmented
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.ResultState
import com.powermath.viewmodel.Screen
import kotlinx.coroutines.delay

@Composable
fun CalculatorScreen(vm: CalcViewModel, onCopy: (String) -> Unit, onPaste: () -> String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Route key actions: clipboard actions need the UI layer, everything else is handled by the ViewModel.
    val onKey: (KeyAction) -> Unit = { action ->
        when (action) {
            KeyAction.CopyResult -> {
                val r = vm.result
                if (r is ResultState.Success) { onCopy(r.outcome.renderText(settings.format)); vm.toast = "Result copied" }
                else vm.toast = "Nothing to copy yet"
            }
            KeyAction.Paste -> onPaste()?.let { vm.insert(it.trim()) }
            else -> vm.onKey(action)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val keyHeight = (((maxHeight * 0.40f) / 6) * settings.keypadSize.scale).coerceIn(32.dp, 66.dp)
        var menuOpen by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ---- slim top bar: angle, result format, menu ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (settings.angle == AngleMode.DEG) "DEG" else "RAD", { vm.toggleAngle() }, active = true,
                    desc = "Angle mode: ${if (settings.angle == AngleMode.DEG) "degrees" else "radians"}. Tap to switch")
                Chip(when (settings.mode) { ResultMode.EXACT -> "Fraction"; ResultMode.DECIMAL -> "Decimal"; ResultMode.SCIENTIFIC -> "Sci" },
                    { vm.setMode(when (settings.mode) { ResultMode.EXACT -> ResultMode.DECIMAL; ResultMode.DECIMAL -> ResultMode.SCIENTIFIC; ResultMode.SCIENTIFIC -> ResultMode.EXACT }) },
                    desc = "Result format. Tap to change between fraction, decimal and scientific")
                Spacer(Modifier.weight(1f))
                Chip("Scan", { vm.scan = com.powermath.viewmodel.ScanState.Choose }, desc = "Scan an expression from a photo")
                Chip("Graph", { vm.openGraph(vm.editor.text) })
                Chip("Tools", { vm.screen = Screen.TOOLS })
                Box {
                    Chip("Menu ▾", { menuOpen = true }, desc = "More: history, workspace, settings")
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Home") }, onClick = { menuOpen = false; vm.screen = Screen.HOME })
                        DropdownMenuItem(text = { Text("Tools: calculus, matrices, units, primes, shapes…") }, onClick = { menuOpen = false; vm.screen = Screen.TOOLS })
                        DropdownMenuItem(text = { Text("History") }, onClick = { menuOpen = false; vm.screen = Screen.HISTORY })
                        DropdownMenuItem(text = { Text("Workspace (variables & functions)") }, onClick = { menuOpen = false; vm.screen = Screen.WORKSPACE })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; vm.screen = Screen.SETTINGS })
                    }
                }
            }


            // ---- result actions: fixed row under the tools bar, never inside the result ----
            var showSteps by remember { mutableStateOf(false) }
            val res = vm.result
            LaunchedEffect(res) { showSteps = settings.autoSteps && res is ResultState.Success && res.outcome.steps.isNotEmpty() }
            if (res is ResultState.Success) {
                val outLines = remember(res, settings.format) { res.outcome.render(settings.format) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Copy", { onCopy(outLines.joinToString("\n")); vm.toast = "Result copied" }, desc = "Copy the result")
                    res.outcome.graphExpr?.let { g -> Chip("Graph result", { vm.plotFromResult(g) }, desc = "Plot the result") }
                    if (res.outcome.value != null) Chip("Save", { vm.onKey(KeyAction.SaveResultAsVar) }, desc = "Save the result as a variable")
                    if (res.outcome.steps.isNotEmpty()) Chip("Steps", { showSteps = true }, desc = "Show the working steps")
                }
                if (showSteps && res.outcome.steps.isNotEmpty()) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showSteps = false },
                        title = { Text("Steps") },
                        text = {
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                res.outcome.steps.forEachIndexed { i, st ->
                                    Text("${i + 1}. $st", fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showSteps = false }) { Text("Close") } }
                    )
                }
            }

            // ---- display: takes all remaining space ----
            Display(vm, settings.format, settings.textSize.scale, settings.autoSteps, com.powermath.ui.theme.fontFamilyOf(settings.font), onCopy, Modifier.weight(1f).fillMaxWidth())

            // ---- modifiers + keypad ----
            ModifierRow(vm.modifiers, vm::toggleShift, vm::toggleAlt, vm::toggleCtrl, vm::toggleAbc)
            val wsNames = vm.wsVars.map { it.name }
            val fnNames = vm.wsFuncs.map { it.name }
            androidx.compose.animation.Crossfade(
                targetState = vm.modifiers,
                animationSpec = androidx.compose.animation.core.tween(if (com.powermath.ui.theme.LocalAnimations.current) 160 else 0),
                label = "keypadLayer"
            ) { m ->
                Keypad(KeyLayouts.layer(m, wsNames, fnNames), keyHeight, settings.haptics, onKey, Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun Display(vm: CalcViewModel, fmt: FormatSettings, textScale: Float, autoSteps: Boolean, font: androidx.compose.ui.text.font.FontFamily?, onCopy: (String) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        vm.toast?.let { msg ->
            LaunchedEffect(msg) { delay(2500); if (vm.toast == msg) vm.toast = null }
            Text(msg, color = cs.primary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ---- expression editor (scrollable, multiline) ----
        val scroll = rememberScrollState()
        val ed = vm.editor
        LaunchedEffect(ed.text, ed.cursor) { scroll.animateScrollTo(scroll.maxValue) }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.BottomEnd) {
            val text = buildAnnotatedString {
                if (ed.text.isEmpty()) {
                    withStyle(SpanStyle(color = cs.onSurfaceVariant)) { append("Enter an expression") }
                    withStyle(SpanStyle(color = cs.primary)) { append("|") }
                } else {
                    append(ed.text.substring(0, ed.cursor))
                    withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)) { append("|") }
                    append(ed.text.substring(ed.cursor))
                }
            }
            Text(
                text, fontSize = (if (ed.text.length > 30) 24.sp else 34.sp) * textScale, textAlign = TextAlign.End, color = cs.onSurface, fontFamily = font,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expression: ${ed.text}" }
            )
        }

        // ---- formatted preview + live result ----
        if (ed.text.isNotEmpty()) {
            Text(Pretty.format(ed.text), fontSize = 14.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.fillMaxWidth())
            vm.livePreview?.let { Text("= $it", fontSize = 16.sp, color = cs.primary.copy(alpha = 0.75f), textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth()) }
        }

        ResultCard(vm, fmt, textScale, autoSteps, font, onCopy)
    }
}

@Composable
private fun ResultCard(vm: CalcViewModel, fmt: FormatSettings, textScale: Float, autoSteps: Boolean, font: androidx.compose.ui.text.font.FontFamily?, onCopy: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).animateContentSize()) {
        androidx.compose.animation.AnimatedContent(
            targetState = vm.result,
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { it / 3 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
            },
            label = "result"
        ) { state ->
        when (val r = state) {
            ResultState.None -> {}
            is ResultState.Failure -> {
                Text(r.message, color = cs.error, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Error: ${r.message}" })
            }
            is ResultState.Success -> {
                val lines = remember(r, fmt) { r.outcome.render(fmt) }
                val multi = lines.size > 1
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.End) {
                    for (l in lines) {
                        val frac = if (multi) null else Regex("^(-?[0-9]+)/([0-9]+)$").matchEntire(l)
                        if (frac != null) {
                            StackedFraction(frac.groupValues[1], frac.groupValues[2], (if (l.length > 10) 30.sp else 40.sp) * textScale, cs.onSurface)
                            continue
                        }
                        Text(
                            l,
                            fontSize = (if (multi) 22.sp else if (l.length > 16) 28.sp else 40.sp) * textScale,
                            fontWeight = FontWeight.Light, color = cs.onSurface, textAlign = TextAlign.End, fontFamily = font,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Result: $l" }
                        )
                    }
                }
            }
        }
        }
    }
}

/** Exact fractions in the result are shown stacked: numerator over a bar over the denominator. */
@Composable
private fun StackedFraction(num: String, den: String, size: androidx.compose.ui.unit.TextUnit, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.width(IntrinsicSize.Max).padding(vertical = 4.dp).semantics { contentDescription = "Result: $num over $den" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(num, fontSize = size, fontWeight = FontWeight.Light, color = color)
        Box(Modifier.fillMaxWidth().height(2.dp).background(color.copy(alpha = 0.8f)))
        Text(den, fontSize = size, fontWeight = FontWeight.Light, color = color)
    }
}
