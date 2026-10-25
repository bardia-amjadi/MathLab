package com.powermath.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermath.viewmodel.ModifierState

@Composable
private fun KeyButton(def: KeyDef, height: Dp, modifier: Modifier, haptics: Boolean, onClick: () -> Unit) {
    if (def.kind == KeyKind.SPACER) { Box(modifier.height(height)); return }
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (def.kind) {
        KeyKind.NUMBER -> cs.surfaceVariant to cs.onSurface
        KeyKind.OPERATOR -> cs.secondaryContainer to cs.onSecondaryContainer
        KeyKind.EQUALS -> cs.primary to cs.onPrimary
        KeyKind.ACTION -> cs.surface to cs.primary
        else -> cs.surface to cs.onSurface
    }
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val anim = com.powermath.ui.theme.LocalAnimations.current
    val scale by animateFloatAsState(if (pressed && anim) 0.92f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "keyScale")
    Surface(
        onClick = { if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        interactionSource = source,
        modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }.semantics { contentDescription = def.description },
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        border = if (def.kind == KeyKind.FUNCTION || def.kind == KeyKind.ACTION) BorderStroke(1.dp, cs.outline.copy(alpha = 0.5f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            val len = def.label.length
            Text(
                def.label,
                fontSize = when { len <= 2 -> 20.sp; len <= 4 -> 16.sp; len <= 7 -> 13.sp; else -> 11.sp },
                fontWeight = if (def.kind == KeyKind.EQUALS) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Keypad(rows: List<List<KeyDef>>, keyHeight: Dp, haptics: Boolean, onKey: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in row) KeyButton(k, keyHeight, Modifier.weight(k.weight), haptics) { onKey(k.action) }
            }
        }
    }
}

@Composable
private fun RowScope.ModifierButton(label: String, active: Boolean, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(30.dp).semantics { contentDescription = desc; selected = active },
        shape = RoundedCornerShape(12.dp),
        color = animateColorAsState(if (active) cs.primary else cs.surface, label = "modBg").value,
        contentColor = if (active) cs.onPrimary else cs.onSurfaceVariant,
        border = BorderStroke(1.dp, if (active) cs.primary else cs.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label + if (active) " ●" else "", fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** Three persistent, visible modifier toggles; the active layer name is shown inline (no extra line). */
@Composable
fun ModifierRow(state: ModifierState, onShift: () -> Unit, onAlt: () -> Unit, onCtrl: () -> Unit, onAbc: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ModifierButton("Shift", state.shift, "Shift modifier, advanced functions", onShift)
        ModifierButton("Alt", state.alt, "Alt modifier, variables and symbols", onAlt)
        ModifierButton("Ctrl", state.ctrl, "Ctrl modifier, workspace actions", onCtrl)
        ModifierButton("ABC", state.abc, "Letter keyboard with all letters and Greek letters", onAbc)
        Text(
            state.layerName.substringBefore(" · "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f).semantics { contentDescription = "Active key layer: ${state.layerName}" }
        )
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f))) {
        Row(Modifier.padding(3.dp)) {
            for ((value, label) in options) {
                val sel = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).height(32.dp).semantics { this.selected = sel; contentDescription = "$label result format" },
                    shape = RoundedCornerShape(9.dp),
                    color = if (sel) cs.primaryContainer else cs.surface,
                    contentColor = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant
                ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) } }
            }
        }
    }
}

@Composable
fun Chip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, desc: String = label) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp).semantics { contentDescription = desc },
        shape = RoundedCornerShape(15.dp),
        color = if (active) cs.primaryContainer else cs.surface,
        contentColor = if (active) cs.onPrimaryContainer else cs.onSurface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f))
    ) { Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) } }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("← Back", onBack, desc = "Go back")
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}
