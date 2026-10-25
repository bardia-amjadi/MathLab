package com.powermath.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.data.AccentPref
import com.powermath.data.FontPref
import com.powermath.data.StartPage
import com.powermath.data.SizePref
import com.powermath.data.ThemePref
import com.powermath.math.AngleMode
import com.powermath.math.ResultMode
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.ui.components.Segmented
import com.powermath.ui.theme.accentSwatch
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen

/** A rounded card with a title; cards slide/fade in one after another when the screen opens. */
@Composable
private fun Section(index: Int, title: String, hint: String? = null, content: @Composable () -> Unit) {
    val animate = com.powermath.ui.theme.LocalAnimations.current
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { if (animate) { kotlinx.coroutines.delay(60L * index); shown = true } }
    AnimatedVisibility(shown, enter = fadeIn(androidx.compose.animation.core.tween(350)) +
        slideInVertically(androidx.compose.animation.core.tween(350)) { it / 5 }) {
        val cs = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(18.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.35f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (hint != null) Text(hint, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Chip("−", { if (value > range.first) onChange(value - 1) }, desc = "Decrease $label")
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        Chip("+", { if (value < range.last) onChange(value + 1) }, desc = "Increase $label")
    }
}

@Composable
private fun AccentPicker(selected: AccentPref, onPick: (AccentPref) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (a in AccentPref.values()) {
            val on = a == selected
            val ring by animateDpAsState(if (on) 3.dp else 0.dp, label = "ring")
            val scale by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "swatchScale")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape).background(accentSwatch(a))
                        .clickable { onPick(a) }
                        .semantics { contentDescription = "${a.label} accent colour"; this.selected = on },
                    contentAlignment = Alignment.Center
                ) {
                    if (ring > 0.dp) Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                }
                Text(a.label, fontSize = 11.sp, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: CalcViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 16.dp)) {
            Section(0, "Accent colour", "Changes keys, highlights and buttons everywhere.") {
                AccentPicker(s.accent) { a -> vm.updateSettings { it.copy(accent = a) } }
            }
            Section(1, "Theme") {
                Segmented(ThemePref.values().map { it to it.label }, s.theme, { t -> vm.updateSettings { it.copy(theme = t) } }, Modifier.fillMaxWidth())
            }
            Section(2, "Keypad size", "Larger keys are easier to hit; smaller keys leave more room for the expression.") {
                Segmented(SizePref.values().map { it to it.label }, s.keypadSize, { z -> vm.updateSettings { it.copy(keypadSize = z) } }, Modifier.fillMaxWidth())
            }
            Section(3, "Expression text size") {
                Segmented(SizePref.values().map { it to it.label }, s.textSize, { z -> vm.updateSettings { it.copy(textSize = z) } }, Modifier.fillMaxWidth())
            }
            Section(4, "Result format") {
                Segmented(listOf(ResultMode.EXACT to "Fraction", ResultMode.DECIMAL to "Decimal", ResultMode.SCIENTIFIC to "Scientific"),
                    s.mode, { m -> vm.updateSettings { it.copy(mode = m) } }, Modifier.fillMaxWidth())
            }
            Section(5, "Decimal places", "Used in Decimal mode. Trailing zeros are removed.") {
                Stepper(s.decimals, 0..50, "decimal places") { n -> vm.updateSettings { it.copy(decimals = n) } }
            }
            Section(6, "Significant digits", "Used in Scientific notation mode.") {
                Stepper(s.sigDigits, 1..50, "significant digits") { n -> vm.updateSettings { it.copy(sigDigits = n) } }
            }
            Section(7, "Angle mode") {
                Segmented(listOf(AngleMode.DEG to "Degrees", AngleMode.RAD to "Radians"), s.angle, { a -> vm.updateSettings { it.copy(angle = a) } }, Modifier.fillMaxWidth())
            }
            Section(8, "Feedback") {
                SwitchRow("Key vibration", s.haptics) { h -> vm.updateSettings { it.copy(haptics = h) } }
            }
            Section(9, "Start page", "Which page opens after the intro.") {
                Segmented(StartPage.values().map { it to it.label }, s.startPage, { p -> vm.updateSettings { it.copy(startPage = p) } }, Modifier.fillMaxWidth())
            }
            Section(10, "Animations", "Turn off for the fastest, calmest experience.") {
                SwitchRow("Animations and transitions", s.animations) { a -> vm.updateSettings { it.copy(animations = a) } }
                SwitchRow("Show the intro when the app opens", s.showIntro) { a -> vm.updateSettings { it.copy(showIntro = a) } }
            }
            Section(11, "Calculator behaviour") {
                SwitchRow("Live result preview while typing", s.livePreview) { a -> vm.updateSettings { it.copy(livePreview = a) } }
                SwitchRow("Open the steps automatically", s.autoSteps) { a -> vm.updateSettings { it.copy(autoSteps = a) } }
                SwitchRow("Keep the screen on", s.keepScreenOn) { a -> vm.updateSettings { it.copy(keepScreenOn = a) } }
            }
            Section(12, "Expression font", "Used for the expression and results.") {
                Segmented(FontPref.values().map { it to it.label }, s.font, { f -> vm.updateSettings { it.copy(font = f) } }, Modifier.fillMaxWidth())
            }
            Section(13, "Data and reset") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Clear history", { vm.dialog = com.powermath.viewmodel.DialogRequest.ConfirmClearHistory }, desc = "Clear all history")
                    Chip("Reset settings", { vm.resetSettings(); vm.toast = "Settings restored to defaults" }, desc = "Restore all settings to their defaults")
                }
            }
            Section(14, "About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.powermath.R.drawable.logo_powermath), contentDescription = "Power Math logo",
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFF03144D)).padding(6.dp)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Power Math 1.2", fontWeight = FontWeight.SemiBold)
                        Text("Works fully offline", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
                Text("Exact arithmetic uses arbitrary-precision rationals; irrational values are kept exact where possible (√, π, e) and marked ≈ when approximated.",
                    fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.powermath.R.drawable.logo_mathlab), contentDescription = "MathLab logo",
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Powered by MathLab", fontWeight = FontWeight.SemiBold, color = cs.primary)
                        Text("Designed & built by MathLab", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
