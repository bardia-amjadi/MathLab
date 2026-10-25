package com.powermath.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.graph.GraphEngine
import com.powermath.graph.Pt
import com.powermath.graph.Viewport
import com.powermath.math.AngleMode
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.ui.theme.GraphColors
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.GraphFn
import com.powermath.viewmodel.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext

/** coerceIn that never throws when the range is inverted (tiny canvases). */
private fun Float.clampTo(lo: Float, hi: Float): Float = if (hi < lo) lo else coerceIn(lo, hi)

/** Draws text from a pre-measured layout, so it can never crash on a label that sits at or past the canvas edge. */
private fun DrawScope.drawLabel(tm: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle) {
    val layout = tm.measure(text, style)
    drawText(layout, topLeft = Offset(x, y))
}

private class Curve(val id: Int, val colorIndex: Int, val segments: List<List<Pt>>)

private fun fmtNum(d: Double): String {
    if (!d.isFinite()) return "?"
    val r = Math.rint(d)
    if (Math.abs(d - r) < 1e-9 && Math.abs(r) < 1e12) return r.toLong().toString()
    return BigDecimal(d).round(MathContext(4)).stripTrailingZeros().toPlainString()
}

@Composable
fun GraphScreen(vm: CalcViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Graph", onBack = { vm.back() }) {
            Chip("Fit", { vm.fit() }, desc = "Fit the view to the functions")
            Spacer(Modifier.width(6.dp))
            Chip("Clear", { vm.clearGraphs() }, desc = "Remove all plotted functions")
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.graphInput,
                onValueChange = { vm.graphInput = it; vm.graphError = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("f(x) = …   or   y = …   or   x^2 - 4") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.plot() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.plot() }) { Text("Plot") }
        }
        vm.graphError?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        // plotted functions: tap to show/hide, × to remove
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (g in vm.graphFns) FunctionChip(g, { vm.toggleGraph(g.id) }, { vm.removeGraph(g.id) })
            val saved = vm.wsFuncs.filter { it.params.size == 1 }
            if (saved.isNotEmpty()) {
                Text("Saved:", fontSize = 12.sp, color = cs.onSurfaceVariant)
                for (f in saved) Chip("${f.name}(${f.params[0]})", { vm.plotSaved(f) }, desc = "Plot saved function ${f.name}")
            }
            Chip(if (vm.showAnnotations) "Roots & vertex: on" else "Roots & vertex: off", { vm.showAnnotations = !vm.showAnnotations }, active = vm.showAnnotations)
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            GraphCanvas(vm, settings.angle, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(cs.surface))
            if (vm.graphFns.isEmpty()) {
                Text(
                    "Enter a function above, e.g. f(x) = x^2 - 4, or tap a saved function.\nPinch to zoom · drag to pan · double-tap to fit · tap to trace",
                    color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }
        }
        AnnotationPanel(vm, settings.angle)
    }
}

@Composable
private fun FunctionChip(g: GraphFn, onToggle: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = GraphColors[g.colorIndex % GraphColors.size]
    Surface(
        onClick = onToggle, shape = RoundedCornerShape(16.dp), color = cs.surface,
        modifier = Modifier.alpha(if (g.visible && g.error == null) 1f else 0.5f),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(g.label, fontSize = 13.sp, maxLines = 1)
            if (g.error != null) Text("  (${g.error})", fontSize = 11.sp, color = cs.error, maxLines = 1)
            Text("  ×", fontSize = 16.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.clickable { onRemove() }.padding(horizontal = 8.dp).semantics { contentDescription = "Remove ${g.label}" })
        }
    }
}

@Composable
private fun AnnotationPanel(vm: CalcViewModel, angle: AngleMode) {
    val cs = MaterialTheme.colorScheme
    val first = vm.graphFns.firstOrNull { it.visible && vm.graphAnnotations[it.id] != null }
    val ann = first?.let { vm.graphAnnotations[it.id] }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (ann != null && first != null) {
            val roots = ann.roots
            Text(
                if (roots.isEmpty()) "${first.label}: no real roots" else "Roots: " + roots.joinToString(", ") { "x = ${fmtNum(it.x)}" },
                fontSize = 13.sp
            )
            if (ann.extrema.isNotEmpty())
                Text((if (ann.isQuadratic) "Vertex: " else "Local extrema: ") + ann.extrema.joinToString(", ") { "(${fmtNum(it.x)}, ${fmtNum(it.y)})" }, fontSize = 13.sp)
            ann.yIntercept?.let { Text("y-intercept: (0, ${fmtNum(it.y)})", fontSize = 13.sp, color = cs.onSurfaceVariant) }
        }
        Text("Angle mode: ${if (angle == AngleMode.DEG) "degrees" else "radians"} (affects sin, cos, tan). Values are numeric.",
            fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun GraphCanvas(vm: CalcViewModel, angle: AngleMode, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val vp = vm.viewport
    val fns = vm.graphFns
    var curves by remember { mutableStateOf<List<Curve>>(emptyList()) }
    var trace by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }

    // Sampling happens off the main thread; the previous curves stay on screen until the new ones are ready.
    LaunchedEffect(fns, vp, vm.wsVersion, size, angle) {
        if (size.width == 0) return@LaunchedEffect
        val samples = (size.width / 2).coerceIn(200, 1200)
        curves = withContext(Dispatchers.Default) {
            fns.filter { it.visible && it.compiled != null }.mapNotNull { g ->
                val c = g.compiled!!
                try { Curve(g.id, g.colorIndex, GraphEngine.sample({ x -> c.eval(x) }, vp, samples)) }
                catch (e: Exception) { null }   // a function that fails to evaluate is skipped instead of crashing the app
            }
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        var v = vm.viewport.pan(pan.x, pan.y, w, h)
                        if (zoom != 1f) v = v.zoom(zoom.toDouble(), centroid.x, centroid.y, w, h)
                        vm.viewport = v
                        trace = null
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { vm.fit(); trace = null },
                    onTap = { off ->
                        val w = size.width.toFloat()
                        val g = vm.graphFns.firstOrNull { it.visible && it.compiled != null }
                        if (g != null && w > 0f) {
                            val x = vm.viewport.toWorldX(off.x, w)
                            val y = try { g.compiled!!.eval(x) } catch (e: Exception) { Double.NaN }
                            trace = if (y.isFinite()) Triple(g.id, x, y) else null
                        }
                    }
                )
            }
            .semantics { contentDescription = "Graph of ${fns.joinToString { it.label }}" }
    ) {
        drawGrid(vp, cs.outline.copy(alpha = 0.22f), cs.onSurface.copy(alpha = 0.7f), cs.onSurfaceVariant, textMeasurer)

        val w = this.size.width
        val h = this.size.height
        for (curve in curves) {
            val color = GraphColors[curve.colorIndex % GraphColors.size]
            val path = Path()
            for (seg in curve.segments) {
                var first = true
                for (p in seg) {
                    val sx = vp.toScreenX(p.x, w)
                    val sy = vp.toScreenY(p.y, h).coerceIn(-50_000f, 50_000f)
                    if (!sx.isFinite() || !sy.isFinite()) continue
                    if (first) { path.moveTo(sx, sy); first = false } else path.lineTo(sx, sy)
                }
            }
            drawPath(path, color, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (vm.showAnnotations) {
            for (g in fns) {
                if (!g.visible) continue
                val a = vm.graphAnnotations[g.id] ?: continue
                val color = GraphColors[g.colorIndex % GraphColors.size]
                for (r in a.roots) marker(vp, r, color, cs.surface, hollow = false, label = "(${fmtNum(r.x)}, 0)", tm = textMeasurer, labelColor = cs.onSurface)
                for (e in a.extrema) marker(vp, e, color, cs.surface, hollow = true, label = "(${fmtNum(e.x)}, ${fmtNum(e.y)})", tm = textMeasurer, labelColor = cs.onSurface)
            }
        }

        trace?.let { (id, x, y) ->
            val color = GraphColors[(fns.firstOrNull { it.id == id }?.colorIndex ?: 0) % GraphColors.size]
            marker(vp, Pt(x, y), color, cs.surface, hollow = true, label = "(${fmtNum(x)}, ${fmtNum(y)})", tm = textMeasurer, labelColor = cs.primary)
        }
    }
}

private fun DrawScope.marker(vp: Viewport, p: Pt, color: Color, bg: Color, hollow: Boolean, label: String,
                             tm: TextMeasurer, labelColor: Color) {
    val sx = vp.toScreenX(p.x, size.width)
    val sy = vp.toScreenY(p.y, size.height)
    if (sx < -20f || sx > size.width + 20f || sy < -20f || sy > size.height + 20f) return
    val r = 5.dp.toPx()
    drawCircle(color, r, Offset(sx, sy))
    if (hollow) drawCircle(bg, r * 0.5f, Offset(sx, sy))
    val tx = (sx + 8.dp.toPx()).clampTo(2f, size.width - 90.dp.toPx())
    val ty = (sy - 20.dp.toPx()).clampTo(2f, size.height - 18.dp.toPx())
    drawLabel(tm, label, tx, ty, TextStyle(fontSize = 11.sp, color = labelColor))
}

private fun DrawScope.drawGrid(vp: Viewport, gridColor: Color, axisColor: Color, labelColor: Color, tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    val xTicks = GraphEngine.ticks(vp.xMin, vp.xMax, 8)
    val yTicks = GraphEngine.ticks(vp.yMin, vp.yMax, 6)
    val xStep = GraphEngine.niceStep(vp.width, 8)
    val yStep = GraphEngine.niceStep(vp.height, 6)
    for (x in xTicks) { val px = vp.toScreenX(x, w); drawLine(gridColor, Offset(px, 0f), Offset(px, h), 1f) }
    for (y in yTicks) { val py = vp.toScreenY(y, h); drawLine(gridColor, Offset(0f, py), Offset(w, py), 1f) }

    val axisX = vp.toScreenX(0.0, w)   // where the y-axis is
    val axisY = vp.toScreenY(0.0, h)   // where the x-axis is
    val showYAxis = axisX in 0f..w
    val showXAxis = axisY in 0f..h
    if (showYAxis) drawLine(axisColor, Offset(axisX, 0f), Offset(axisX, h), 2f)
    if (showXAxis) drawLine(axisColor, Offset(0f, axisY), Offset(w, axisY), 2f)

    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    val labelY = (if (showXAxis) axisY else if (axisY < 0f) 0f else h - 14.dp.toPx()).clampTo(0f, h - 14.dp.toPx()) + 2.dp.toPx()
    val labelX = (if (showYAxis) axisX else if (axisX < 0f) 0f else w - 34.dp.toPx()).clampTo(0f, w - 34.dp.toPx()) + 4.dp.toPx()
    for (x in xTicks) {
        if (Math.abs(x) < xStep * 1e-9) continue
        drawLabel(tm, GraphEngine.tickLabel(x, xStep), vp.toScreenX(x, w) + 2.dp.toPx(), labelY, style)
    }
    for (y in yTicks) {
        if (Math.abs(y) < yStep * 1e-9) continue
        drawLabel(tm, GraphEngine.tickLabel(y, yStep), labelX, vp.toScreenY(y, h) - 12.dp.toPx(), style)
    }
    if (showXAxis && showYAxis) drawLabel(tm, "0", axisX + 4.dp.toPx(), axisY + 2.dp.toPx(), style)
}
