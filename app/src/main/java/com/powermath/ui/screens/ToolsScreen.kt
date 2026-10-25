package com.powermath.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.math.Calculator
import com.powermath.math.Formatter
import com.powermath.math.FormatSettings
import com.powermath.math.Geometry
import com.powermath.math.Identities
import com.powermath.math.MathError
import com.powermath.math.Ops
import com.powermath.math.Rat
import com.powermath.math.ResultMode
import com.powermath.math.Shape
import com.powermath.math.Stats
import com.powermath.math.UnitCircle
import com.powermath.math.Value
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.ui.theme.GraphColors
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen

private enum class ToolTab(val label: String) {
    CALCULUS("Calculus"), NUMBERS("Numbers"), MATRIX("Matrices"), UNITS("Units"), UNIT_CIRCLE("Unit circle"), SHAPES("Shapes"), CHARTS("Charts"), SEQUENCES("Sequences"), IDENTITIES("Identities")
}

@Composable
fun ToolsScreen(vm: CalcViewModel) {
    var tab by rememberSaveable { mutableStateOf(ToolTab.CALCULUS.name) }
    val current = ToolTab.valueOf(tab)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tools", onBack = { vm.back() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in ToolTab.values()) Chip(t.label, { tab = t.name }, active = t == current, desc = "${t.label} tool")
        }
        Box(Modifier.fillMaxSize()) {
            when (current) {
                ToolTab.CALCULUS -> CalculusTab(vm)
                ToolTab.NUMBERS -> NumbersTab(vm)
                ToolTab.MATRIX -> MatrixTab(vm)
                ToolTab.UNITS -> UnitsTab(vm)
                ToolTab.UNIT_CIRCLE -> UnitCircleTab(vm)
                ToolTab.SHAPES -> ShapesTab(vm)
                ToolTab.CHARTS -> ChartsTab(vm)
                ToolTab.SEQUENCES -> SequencesTab(vm)
                ToolTab.IDENTITIES -> IdentitiesTab(vm)
            }
        }
    }
}

private fun exactText(v: Value?): String = if (v == null) "undefined" else Formatter.format(v, FormatSettings(ResultMode.EXACT, 6, 6))

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = cs.surface) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

// ============================================================ unit circle
@Composable
private fun UnitCircleTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    var deg by rememberSaveable { mutableStateOf(30) }
    val point = UnitCircle.at(deg)
    val rad = Math.toRadians(deg.toDouble())
    val tm = rememberTextMeasurer()
    val cosColor = GraphColors[0]; val sinColor = GraphColors[1]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        val cx = size.width / 2f; val cy = size.height / 2f
                        var a = Math.toDegrees(Math.atan2((cy - off.y).toDouble(), (off.x - cx).toDouble()))
                        if (a < 0) a += 360.0
                        deg = Math.round(a).toInt().coerceIn(0, 360)
                    }
                }
                .semantics { contentDescription = "Unit circle, angle $deg degrees. Tap to choose an angle." }
        ) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val r = minOf(size.width, size.height) / 2f * 0.78f
            val axis = cs.onSurfaceVariant.copy(alpha = 0.6f)
            drawLine(axis, Offset(cx - r * 1.2f, cy), Offset(cx + r * 1.2f, cy), 2f)
            drawLine(axis, Offset(cx, cy - r * 1.2f), Offset(cx, cy + r * 1.2f), 2f)
            drawCircle(cs.onSurface.copy(alpha = 0.8f), r, Offset(cx, cy), style = Stroke(3f))
            // special-angle ticks
            for (a in UnitCircle.specialAngles.dropLast(1)) {
                val t = Math.toRadians(a.toDouble())
                drawCircle(cs.onSurfaceVariant, 3.5f, Offset(cx + r * Math.cos(t).toFloat(), cy - r * Math.sin(t).toFloat()))
            }
            val px = cx + r * Math.cos(rad).toFloat(); val py = cy - r * Math.sin(rad).toFloat()
            // angle arc
            drawArc(cs.primary.copy(alpha = 0.5f), 0f, -deg.toFloat(), false, Offset(cx - r * 0.25f, cy - r * 0.25f), Size(r * 0.5f, r * 0.5f), style = Stroke(4f))
            // projections: cos (horizontal), sin (vertical)
            drawLine(cosColor, Offset(cx, cy), Offset(px, cy), 6f, StrokeCap.Round)
            drawLine(sinColor, Offset(px, cy), Offset(px, py), 6f, StrokeCap.Round)
            drawLine(cs.primary, Offset(cx, cy), Offset(px, py), 4f, StrokeCap.Round)
            drawCircle(cs.primary, 10f, Offset(px, py))
            val st = TextStyle(fontSize = 11.sp, color = cs.onSurface)
            drawLabel(tm, "1", cx + r + 6f, cy + 4f, st); drawLabel(tm, "-1", cx - r - 22f, cy + 4f, st)
            drawLabel(tm, "1", cx + 6f, cy - r - 16f, st); drawLabel(tm, "-1", cx + 6f, cy + r + 2f, st)
        }
        Slider(value = deg.toFloat(), onValueChange = { deg = Math.round(it) }, valueRange = 0f..360f,
            modifier = Modifier.semantics { contentDescription = "Angle in degrees" })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (a in UnitCircle.specialAngles) Chip("$a°", { deg = a }, active = a == deg, desc = "Angle $a degrees")
        }
        Card(Modifier.padding(top = 8.dp)) {
            Text("θ = $deg°", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (point != null) {
                Text("= ${exactText(point.radians)} rad", fontSize = 16.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("cos θ = ${exactText(point.cos)}", color = cosColor, fontSize = 18.sp)
                Text("sin θ = ${exactText(point.sin)}", color = sinColor, fontSize = 18.sp)
                Text("tan θ = ${exactText(point.tan)}", fontSize = 18.sp)
                Text("Point on the circle: (${exactText(point.cos)}, ${exactText(point.sin)})", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text("= ${fmt4(rad / Math.PI)}π ≈ ${fmt4(rad)} rad", fontSize = 16.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("cos θ ≈ ${fmt4(Math.cos(rad))}", color = cosColor, fontSize = 18.sp)
                Text("sin θ ≈ ${fmt4(Math.sin(rad))}", color = sinColor, fontSize = 18.sp)
                val tn = Math.tan(rad)
                Text("tan θ ≈ ${if (Math.abs(Math.cos(rad)) < 1e-9) "undefined" else fmt4(tn)}", fontSize = 18.sp)
                Text("Not a special angle, so the values are approximate. Pick a multiple of 30° or 45° for exact values.", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun fmt4(d: Double): String = java.math.BigDecimal(d).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

/** Draws text from a measured layout (never crashes near the canvas edge). */
private fun DrawScope.drawLabel(tm: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle) {
    drawText(tm.measure(text, style), topLeft = Offset(x, y))
}

// ============================================================ shapes
@Composable
private fun ShapesTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    val s by vm.settings.collectAsStateWithLifecycle()
    var shapeName by rememberSaveable { mutableStateOf(Shape.CIRCLE.name) }
    val shape = Shape.valueOf(shapeName)
    var inputs by remember(shapeName) { mutableStateOf(List(shape.params.size) { "" }) }

    val values: List<Value?> = inputs.map { t -> if (t.isBlank()) null else try { Calculator.evalValue(t, vm.workspace, vm.toolSettings()) } catch (e: MathError) { null } }
    var error: String? = null
    var result: com.powermath.math.GeoResult? = null
    if (values.all { it != null }) {
        try { result = Geometry.compute(shape, values.map { it!! }) } catch (e: MathError) { error = e.message }
    } else if (inputs.any { it.isNotBlank() } && inputs.withIndex().any { (i, t) -> t.isNotBlank() && values[i] == null }) error = "Enter numbers like 3, 2.5, 3/2 or sqrt(2)"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Flat shapes", fontSize = 12.sp, color = cs.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (sh in Shape.values().filter { !it.solid }) Chip(sh.title, { shapeName = sh.name }, active = sh == shape)
        }
        Text("Solids", fontSize = 12.sp, color = cs.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (sh in Shape.values().filter { it.solid }) Chip(sh.title, { shapeName = sh.name }, active = sh == shape)
        }

        Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
            .semantics { contentDescription = "Drawing of a ${shape.title}" }) {
            drawShape(shape, values.map { it?.let { v -> Ops.toDouble(v) } }, cs.primary, cs.onSurfaceVariant)
        }

        Card(Modifier.padding(top = 4.dp)) {
            shape.params.forEachIndexed { i, label ->
                OutlinedTextField(
                    value = inputs[i], onValueChange = { n -> inputs = inputs.toMutableList().also { it[i] = n } },
                    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                )
            }
        }
        error?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        result?.let { r ->
            Card {
                for ((name, v) in r.rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(name, modifier = Modifier.weight(1f), color = cs.onSurfaceVariant)
                        Text(Formatter.format(v, s.format), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                }
                if (s.mode == ResultMode.EXACT) Text("Exact values; switch the result format in Settings for decimals.", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(6.dp))
                for (st in r.steps) Text("• $st", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Simple schematic drawings, scaled to fit. Missing values fall back to a pleasant default proportion. */
private fun DrawScope.drawShape(shape: Shape, vals: List<Double?>, line: Color, soft: Color) {
    fun v(i: Int, d: Double) = vals.getOrNull(i)?.takeIf { it.isFinite() && it > 0 } ?: d
    val w = size.width; val h = size.height
    val fill = line.copy(alpha = 0.18f)
    val stroke = Stroke(width = 4f)

    /** world points (y up) -> canvas, keeping the aspect ratio with a margin */
    fun fit(pts: List<Pair<Double, Double>>): List<Offset> {
        val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
        val sx = (w * 0.8) / (maxX - minX).coerceAtLeast(1e-9); val sy = (h * 0.8) / (maxY - minY).coerceAtLeast(1e-9)
        val sc = minOf(sx, sy)
        val ox = (w - (maxX - minX) * sc) / 2.0; val oy = (h - (maxY - minY) * sc) / 2.0
        return pts.map { Offset((ox + (it.first - minX) * sc).toFloat(), (h - (oy + (it.second - minY) * sc)).toFloat()) }
    }
    fun polygon(pts: List<Pair<Double, Double>>) {
        val o = fit(pts)
        val p = Path().apply { moveTo(o[0].x, o[0].y); for (i in 1 until o.size) lineTo(o[i].x, o[i].y); close() }
        drawPath(p, fill); drawPath(p, line, style = stroke)
    }
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, filled: Boolean = false) {
        if (filled) drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
        drawOval(line, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = stroke)
    }
    fun seg(a: Offset, b: Offset, dashed: Boolean = false) = drawLine(if (dashed) soft else line, a, b, 4f)

    when (shape) {
        Shape.CIRCLE -> { val r = minOf(w, h) * 0.38f; drawCircle(fill, r, Offset(w / 2, h / 2)); drawCircle(line, r, Offset(w / 2, h / 2), style = stroke); seg(Offset(w / 2, h / 2), Offset(w / 2 + r, h / 2)) }
        Shape.RECTANGLE -> polygon(listOf(0.0 to 0.0, v(0, 4.0) to 0.0, v(0, 4.0) to v(1, 3.0), 0.0 to v(1, 3.0)))
        Shape.SQUARE -> polygon(listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0))
        Shape.TRIANGLE -> polygon(listOf(0.0 to 0.0, v(0, 4.0) to 0.0, v(0, 4.0) / 2 to v(1, 3.0)))
        Shape.TRIANGLE_SSS -> {
            val a = v(0, 3.0); val b = v(1, 4.0); val c = v(2, 5.0)
            val x = (b * b + c * c - a * a) / (2 * c); val y2 = b * b - x * x
            if (y2 > 0) polygon(listOf(0.0 to 0.0, c to 0.0, x to Math.sqrt(y2))) else polygon(listOf(0.0 to 0.0, 4.0 to 0.0, 2.0 to 3.0))
        }
        Shape.RIGHT_TRIANGLE -> polygon(listOf(0.0 to 0.0, v(0, 4.0) to 0.0, 0.0 to v(1, 3.0)))
        Shape.TRAPEZOID -> { val a = v(0, 6.0); val b = v(1, 3.0); val hh = v(2, 3.0); val off = (a - b) / 2; polygon(listOf(0.0 to 0.0, a to 0.0, off + b to hh, off to hh)) }
        Shape.PARALLELOGRAM -> { val b = v(0, 5.0); val hh = v(1, 3.0); polygon(listOf(0.0 to 0.0, b to 0.0, b + b * 0.3 to hh, b * 0.3 to hh)) }
        Shape.RHOMBUS -> { val d1 = v(0, 6.0); val d2 = v(1, 4.0); polygon(listOf(0.0 to d2 / 2, d1 / 2 to 0.0, d1 to d2 / 2, d1 / 2 to d2)) }
        Shape.ELLIPSE -> { val a = v(0, 3.0); val b = v(1, 2.0); val sc = minOf(w * 0.4 / a, h * 0.4 / b).toFloat(); ellipse(w / 2, h / 2, (a * sc).toFloat(), (b * sc).toFloat(), true) }
        Shape.SECTOR -> {
            val r = minOf(w, h) * 0.4f; val ang = (vals.getOrNull(1) ?: 60.0).coerceIn(1.0, 360.0).toFloat()
            drawArc(fill, -ang / 2f, ang, true, Offset(w / 2 - r, h / 2 - r), Size(r * 2, r * 2))
            drawArc(line, -ang / 2f, ang, true, Offset(w / 2 - r, h / 2 - r), Size(r * 2, r * 2), style = stroke)
        }
        Shape.REGULAR_POLYGON -> {
            val n = (vals.getOrNull(0) ?: 5.0).let { if (it.isFinite()) it.toInt().coerceIn(3, 60) else 5 }
            polygon((0 until n).map { i -> val t = Math.PI / 2 + 2 * Math.PI * i / n; Math.cos(t) to Math.sin(t) })
        }
        Shape.SPHERE -> { val r = minOf(w, h) * 0.38f; drawCircle(fill, r, Offset(w / 2, h / 2)); drawCircle(line, r, Offset(w / 2, h / 2), style = stroke); ellipse(w / 2, h / 2, r, r * 0.3f) }
        Shape.CUBE, Shape.CUBOID -> {
            val l = if (shape == Shape.CUBE) 1.0 else v(0, 3.0); val wd = if (shape == Shape.CUBE) 1.0 else v(1, 2.0); val ht = if (shape == Shape.CUBE) 1.0 else v(2, 2.0)
            val dx = wd * 0.5; val dy = wd * 0.35
            val front = fit(listOf(0.0 to 0.0, l to 0.0, l to ht, 0.0 to ht, dx to dy, l + dx to dy, l + dx to ht + dy, dx to ht + dy))
            val f = Path().apply { moveTo(front[0].x, front[0].y); lineTo(front[1].x, front[1].y); lineTo(front[2].x, front[2].y); lineTo(front[3].x, front[3].y); close() }
            drawPath(f, fill); drawPath(f, line, style = stroke)
            val back = Path().apply { moveTo(front[3].x, front[3].y); lineTo(front[7].x, front[7].y); lineTo(front[6].x, front[6].y); lineTo(front[2].x, front[2].y) }
            drawPath(back, line, style = stroke); seg(front[1], front[5]); seg(front[5], front[6]); seg(front[0], front[4], true); seg(front[4], front[5], true); seg(front[4], front[7], true)
        }
        Shape.CYLINDER -> {
            val r = v(0, 2.0); val ht = v(1, 3.0); val sc = minOf(w * 0.35 / r, h * 0.7 / (ht + r * 0.6)).toFloat()
            val rx = (r * sc).toFloat(); val ry = rx * 0.3f; val hh = (ht * sc).toFloat(); val cx = w / 2f; val top = (h - hh) / 2f
            drawRect(fill, Offset(cx - rx, top), Size(rx * 2, hh)); seg(Offset(cx - rx, top), Offset(cx - rx, top + hh)); seg(Offset(cx + rx, top), Offset(cx + rx, top + hh))
            ellipse(cx, top + hh, rx, ry); ellipse(cx, top, rx, ry, true)
        }
        Shape.CONE -> {
            val r = v(0, 2.0); val ht = v(1, 3.0); val sc = minOf(w * 0.35 / r, h * 0.7 / (ht + r * 0.6)).toFloat()
            val rx = (r * sc).toFloat(); val ry = rx * 0.3f; val hh = (ht * sc).toFloat(); val cx = w / 2f; val apexY = (h - hh) / 2f
            val p = Path().apply { moveTo(cx, apexY); lineTo(cx - rx, apexY + hh); lineTo(cx + rx, apexY + hh); close() }
            drawPath(p, fill); seg(Offset(cx, apexY), Offset(cx - rx, apexY + hh)); seg(Offset(cx, apexY), Offset(cx + rx, apexY + hh)); ellipse(cx, apexY + hh, rx, ry)
            seg(Offset(cx, apexY), Offset(cx, apexY + hh), true)
        }
    }
}

// ============================================================ charts
private enum class ChartKind(val label: String) { BAR("Bar"), LINE("Line"), PIE("Pie") }
private class DataPoint(val label: String, val value: Value)

private fun parseData(text: String, vm: CalcViewModel): Pair<List<DataPoint>, String?> {
    val parts = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val out = ArrayList<DataPoint>()
    for ((i, p) in parts.withIndex()) {
        val label: String; val numText: String
        if (p.contains(':')) { label = p.substringBefore(':').trim(); numText = p.substringAfter(':').trim() } else { label = "${i + 1}"; numText = p }
        try { out.add(DataPoint(label.ifEmpty { "${i + 1}" }, Calculator.evalValue(numText, vm.workspace, vm.toolSettings()))) }
        catch (e: MathError) { return Pair(emptyList(), "Could not read \"$p\": ${e.message}") }
    }
    return Pair(out, null)
}

@Composable
private fun ChartsTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    val s by vm.settings.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("Mon:3, Tue:5, Wed:8, Thu:6, Fri:9") }
    var kindName by rememberSaveable { mutableStateOf(ChartKind.BAR.name) }
    val kind = ChartKind.valueOf(kindName)
    val (data, err) = parseData(text, vm)
    val tm = rememberTextMeasurer()
    val stats = if (data.isNotEmpty() && data.all { it.value is Value.Q }) try { Stats.analyze(data.map { (it.value as Value.Q).r }) } catch (e: MathError) { null } else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text("Data: numbers, or label:value pairs") },
            supportingText = { Text("Separate with commas or new lines, e.g. 4, 8, 15 or A:4, B:8") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in ChartKind.values()) Chip(k.label, { kindName = k.name }, active = k == kind, desc = "${k.label} chart")
        }
        err?.let { Text(it, color = cs.error, fontSize = 13.sp) }
        if (data.isNotEmpty()) {
            val nums = data.map { Ops.toDouble(it.value) }
            Canvas(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(cs.surface)
                .semantics { contentDescription = "${kind.label} chart of ${data.size} values" }) {
                when (kind) {
                    ChartKind.BAR -> drawBars(data.map { it.label }, nums, tm, cs.primary, cs.onSurface, cs.onSurfaceVariant)
                    ChartKind.LINE -> drawLineChart(data.map { it.label }, nums, tm, cs.primary, cs.onSurface, cs.onSurfaceVariant)
                    ChartKind.PIE -> drawPie(nums)
                }
            }
            if (kind == ChartKind.PIE) {
                val total = nums.filter { it > 0 }.sum()
                Card(Modifier.padding(top = 6.dp)) {
                    data.forEachIndexed { i, d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(GraphColors[i % GraphColors.size]))
                            Text("  ${d.label}", modifier = Modifier.weight(1f))
                            val pct = if (nums[i] > 0 && total > 0) nums[i] / total * 100 else 0.0
                            Text("${Formatter.format(d.value, s.format)}  (${fmt1(pct)}%)")
                        }
                    }
                    if (nums.any { it < 0 }) Text("Negative values are not shown in a pie chart.", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        if (stats != null) {
            Card(Modifier.padding(top = 4.dp)) {
                Text("Statistics", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                fun q(r: Rat) = Formatter.format(Value.Q(r), s.format)
                StatRow("Count", stats.count.toString()); StatRow("Sum", q(stats.sum)); StatRow("Mean", q(stats.mean)); StatRow("Median", q(stats.median))
                StatRow("Mode", if (stats.modes.isEmpty()) "none" else stats.modes.joinToString(", ") { q(it) })
                StatRow("Minimum", q(stats.min)); StatRow("Maximum", q(stats.max)); StatRow("Range", q(stats.range))
                StatRow("Variance (population)", q(stats.variancePop)); StatRow("Std. deviation (population)", Formatter.format(stats.stdPop, s.format))
                stats.varianceSample?.let { StatRow("Variance (sample)", q(it)) }
                stats.stdSample?.let { StatRow("Std. deviation (sample)", Formatter.format(it, s.format)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatRow(n: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(n, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.Medium)
    }
}

private fun fmt1(d: Double) = java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()

private fun DrawScope.drawBars(labels: List<String>, vals: List<Double>, tm: TextMeasurer, bar: Color, text: Color, soft: Color) {
    val left = 44f; val bottom = 30f; val top = 16f; val right = 12f
    val plotW = size.width - left - right; val plotH = size.height - top - bottom
    val maxV = maxOf(vals.maxOrNull() ?: 1.0, 0.0); val minV = minOf(vals.minOrNull() ?: 0.0, 0.0)
    val span = (maxV - minV).let { if (it <= 0) 1.0 else it }
    fun y(v: Double) = top + plotH - ((v - minV) / span * plotH).toFloat()
    val st = TextStyle(fontSize = 10.sp, color = soft)
    for (i in 0..4) {
        val v = minV + span * i / 4; val yy = y(v)
        drawLine(soft.copy(alpha = 0.25f), Offset(left, yy), Offset(size.width - right, yy), 1f)
        drawLabel(tm, fmt4(v), 2f, yy - 7f, st)
    }
    val n = vals.size; val slot = plotW / n; val bw = slot * 0.6f
    vals.forEachIndexed { i, v ->
        val x = left + slot * i + (slot - bw) / 2f; val y0 = y(0.0); val y1 = y(v)
        drawRect(bar, Offset(x, minOf(y0, y1)), Size(bw, Math.abs(y0 - y1).coerceAtLeast(1f)))
        if (n <= 12) drawLabel(tm, labels[i].take(6), x - 2f, size.height - bottom + 6f, st)
    }
    drawLine(text.copy(alpha = 0.6f), Offset(left, y(0.0)), Offset(size.width - right, y(0.0)), 2f)
}

private fun DrawScope.drawLineChart(labels: List<String>, vals: List<Double>, tm: TextMeasurer, color: Color, text: Color, soft: Color) {
    val left = 44f; val bottom = 30f; val top = 16f; val right = 16f
    val plotW = size.width - left - right; val plotH = size.height - top - bottom
    val maxV = vals.maxOrNull() ?: 1.0; val minV = vals.minOrNull() ?: 0.0
    val span = (maxV - minV).let { if (it <= 0) 1.0 else it }
    fun y(v: Double) = top + plotH - ((v - minV) / span * plotH).toFloat()
    fun x(i: Int) = if (vals.size == 1) left + plotW / 2 else left + plotW * i / (vals.size - 1)
    val st = TextStyle(fontSize = 10.sp, color = soft)
    for (i in 0..4) {
        val v = minV + span * i / 4; val yy = y(v)
        drawLine(soft.copy(alpha = 0.25f), Offset(left, yy), Offset(size.width - right, yy), 1f)
        drawLabel(tm, fmt4(v), 2f, yy - 7f, st)
    }
    val p = Path()
    vals.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
    drawPath(p, color, style = Stroke(5f, cap = StrokeCap.Round))
    vals.forEachIndexed { i, v ->
        drawCircle(color, 8f, Offset(x(i), y(v))); drawCircle(Color.Black.copy(alpha = 0.25f), 3f, Offset(x(i), y(v)))
        if (vals.size <= 12) drawLabel(tm, labels[i].take(6), x(i) - 10f, size.height - bottom + 6f, st)
    }
}

private fun DrawScope.drawPie(vals: List<Double>) {
    val pos = vals.map { if (it > 0) it else 0.0 }; val total = pos.sum()
    val r = minOf(size.width, size.height) * 0.42f; val tl = Offset(size.width / 2 - r, size.height / 2 - r)
    if (total <= 0) return
    var start = -90f
    pos.forEachIndexed { i, v ->
        val sweep = (v / total * 360.0).toFloat()
        drawArc(GraphColors[i % GraphColors.size], start, sweep, true, tl, Size(r * 2, r * 2))
        start += sweep
    }
}

// ============================================================ sequences
@Composable
private fun SequencesTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    val s by vm.settings.collectAsStateWithLifecycle()
    var terms by rememberSaveable { mutableStateOf("2, 6, 18, 54") }
    var kind by rememberSaveable { mutableStateOf("geom") }
    var a1 by rememberSaveable { mutableStateOf("3") }
    var step by rememberSaveable { mutableStateOf("2") }
    var count by rememberSaveable { mutableStateOf("8") }

    fun run(cmd: String): Pair<List<String>, List<String>>? = try {
        val o = Calculator.process(cmd, vm.workspace, vm.toolSettings()); Pair(o.render(s.format), o.steps)
    } catch (e: MathError) { null }
    val findText = terms.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
    var findErr: String? = null
    var found: Pair<List<String>, List<String>>? = null
    if (findText.isNotEmpty()) try { val o = Calculator.process("seq($findText)", vm.workspace, vm.toolSettings()); found = Pair(o.render(s.format), o.steps) } catch (e: MathError) { findErr = e.message }

    val n = count.trim().toIntOrNull()?.coerceIn(1, 30)
    val gen = ArrayList<String>(); var genErr: String? = null
    if (n != null && a1.isNotBlank() && step.isNotBlank()) {
        try {
            val v1 = Calculator.evalValue(a1, vm.workspace, vm.toolSettings()); val v2 = Calculator.evalValue(step, vm.workspace, vm.toolSettings())
            val f = if (kind == "geom") com.powermath.math.Sequences::geomTerm else com.powermath.math.Sequences::arithTerm
            gen.add((1..n).joinToString(", ") { Formatter.format(f(v1, v2, Value.of(it.toLong())), s.format) })
            val sumF = if (kind == "geom") com.powermath.math.Sequences::geomSum else com.powermath.math.Sequences::arithSum
            gen.add("Sum of the first $n terms = " + Formatter.format(sumF(v1, v2, Value.of(n.toLong())), s.format))
            if (kind == "geom") try { gen.add("Sum to infinity = " + Formatter.format(com.powermath.math.Sequences.geomInfinite(v1, v2), s.format)) } catch (e: MathError) { gen.add("The infinite sum does not exist (|r| ≥ 1).") }
            gen.add(if (kind == "geom") "aₙ = a₁·r^(n − 1),  Sₙ = a₁(1 − rⁿ)/(1 − r)" else "aₙ = a₁ + (n − 1)d,  Sₙ = n(2a₁ + (n − 1)d)/2")
        } catch (e: MathError) { genErr = e.message }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Card {
            Text("Find the pattern", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(terms, { terms = it }, label = { Text("Terms, e.g. 2, 6, 18, 54") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            findErr?.let { Text(it, color = cs.error, fontSize = 13.sp) }
            found?.let { (lines, steps) ->
                Spacer(Modifier.height(8.dp))
                for (l in lines) Text(l, fontSize = 15.sp, modifier = Modifier.padding(vertical = 1.dp))
                for (st in steps) Text("• $st", fontSize = 12.sp, color = cs.onSurfaceVariant)
                TextButton(onClick = { vm.useInCalculator("seq($findText)") }) { Text("Open in calculator") }
            }
            Text("Detects arithmetic, geometric, quadratic and Fibonacci-type patterns.", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Card {
            Text("Generate a sequence", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Geometric", { kind = "geom" }, active = kind == "geom"); Chip("Arithmetic", { kind = "arith" }, active = kind == "arith")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(a1, { a1 = it }, label = { Text("a₁") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(step, { step = it }, label = { Text(if (kind == "geom") "ratio r" else "difference d") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(count, { count = it }, label = { Text("terms") }, singleLine = true, modifier = Modifier.weight(0.7f))
            }
            genErr?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(6.dp))
            for ((i, l) in gen.withIndex()) Text(l, fontSize = if (i == 0) 16.sp else 14.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (i == gen.size - 1) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.padding(vertical = 1.dp))
        }
        Card {
            Text("Calculator functions", fontWeight = FontWeight.SemiBold)
            Text("arith(a₁, d, n)   arithsum(a₁, d, n)\ngeom(a₁, r, n)   geomsum(a₁, r, n)   geominf(a₁, r)\nseq(t₁, t₂, t₃, …)", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================ identities
@Composable
private fun IdentitiesTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        var last = ""
        for (id in Identities.all) {
            if (id.category != last) { last = id.category; Text(id.category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) }
            Card {
                Text(id.name, fontSize = 13.sp, color = cs.onSurfaceVariant)
                Text(id.display, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Try: ${id.example}", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.useInCalculator(id.example) }) { Text("Use") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
