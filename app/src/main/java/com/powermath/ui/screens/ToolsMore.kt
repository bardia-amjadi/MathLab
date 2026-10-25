package com.powermath.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.math.Calculator
import com.powermath.math.Formatter
import com.powermath.math.MathError
import com.powermath.math.Matrix
import com.powermath.math.NumberTheory
import com.powermath.math.Rat
import com.powermath.math.UnitCategory
import com.powermath.math.Units
import com.powermath.ui.components.Chip
import com.powermath.viewmodel.CalcViewModel
import java.math.BigInteger

/** Runs a calculator command and returns (lines, steps) or an error message. */
private fun runCommand(vm: CalcViewModel, text: String, fmt: com.powermath.math.FormatSettings): Pair<Pair<List<String>, List<String>>?, String?> =
    try { val o = Calculator.process(text, vm.workspace, vm.toolSettings()); Pair(Pair(o.render(fmt), o.steps), null) }
    catch (e: MathError) { Pair(null, e.message) }

@Composable
private fun Output(lines: List<String>?, steps: List<String> = emptyList(), error: String?, mono: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    error?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
    lines?.forEach { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = if (mono) FontFamily.Monospace else null, modifier = Modifier.padding(vertical = 2.dp)) }
    steps.forEach { Text("• $it", fontSize = 12.sp, color = cs.onSurfaceVariant) }
}

// ============================================================ calculus
@Composable
internal fun CalculusTab(vm: CalcViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var expr by rememberSaveable { mutableStateOf("x^3 + 2x") }
    var variable by rememberSaveable { mutableStateOf("x") }
    var at by rememberSaveable { mutableStateOf("") }
    var lo by rememberSaveable { mutableStateOf("") }
    var hi by rememberSaveable { mutableStateOf("") }
    val v = variable.trim().ifEmpty { "x" }
    val body = expr.trim()
    val diffCmd = if (body.isEmpty()) null else if (at.isBlank()) "diff($body, $v)" else "diff($body, $v, $at)"
    val intCmd = if (body.isEmpty()) null else if (lo.isBlank() || hi.isBlank()) "integrate($body, $v)" else "integrate($body, $v, $lo, $hi)"
    val d = diffCmd?.let { runCommand(vm, it, s.format) }
    val i = intCmd?.let { runCommand(vm, it, s.format) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Card {
            Text("Function", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(expr, { expr = it }, label = { Text("f(x), e.g. x^2 sin(x)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(variable, { variable = it }, label = { Text("var") }, singleLine = true, modifier = Modifier.weight(0.3f))
            }
        }
        Card {
            Text("Derivative", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(at, { at = it }, label = { Text("Evaluate at $v = … (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Output(d?.first?.first, d?.first?.second ?: emptyList(), d?.second)
            if (diffCmd != null) TextButton(onClick = { vm.useInCalculator(diffCmd) }) { Text("Open in calculator") }
        }
        Card {
            Text("Integral", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(lo, { lo = it }, label = { Text("from (optional)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(hi, { hi = it }, label = { Text("to") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Output(i?.first?.first, i?.first?.second ?: emptyList(), i?.second)
            if (intCmd != null) TextButton(onClick = { vm.useInCalculator(intCmd) }) { Text("Open in calculator") }
        }
        Card {
            Text("In the calculator", fontWeight = FontWeight.SemiBold)
            Text("diff(f, x)   diff(f, x, a)\nintegrate(f, x)   integrate(f, x, a, b)\nWithout bounds the integral gets + C. Functions without a closed form are integrated numerically between bounds.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================ numbers
@Composable
internal fun NumbersTab(vm: CalcViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var n by rememberSaveable { mutableStateOf("360") }
    var base by rememberSaveable { mutableStateOf("2") }
    var digits by rememberSaveable { mutableStateOf("FF") }
    var fromBase by rememberSaveable { mutableStateOf("16") }
    val nt = n.trim()
    val facts: List<Pair<String, Pair<Pair<List<String>, List<String>>?, String?>>> =
        if (nt.isEmpty()) emptyList() else listOf("Prime?" to "isprime($nt)", "Prime factors" to "pfactor($nt)", "Divisors" to "divisors($nt)", "Euler φ" to "totient($nt)", "Next prime" to "nextprime($nt)")
            .map { (title, cmd) -> title to runCommand(vm, cmd, s.format) }
    var convOut: List<String>? = null; var convErr: String? = null
    try {
        val b = base.trim().toIntOrNull() ?: throw MathError(com.powermath.math.ErrorKind.SYNTAX, "Enter a base from 2 to 36")
        if (nt.isNotEmpty()) convOut = listOf("$nt in base $b = ${NumberTheory.toBase(NumberTheory.toBig(Calculator.evalValue(nt, vm.workspace, vm.toolSettings())), b)}")
    } catch (e: MathError) { convErr = e.message }
    var parsed: String? = null; var parseErr: String? = null
    try {
        val fb = fromBase.trim().toIntOrNull() ?: throw MathError(com.powermath.math.ErrorKind.SYNTAX, "Enter a base from 2 to 36")
        if (digits.isNotBlank()) parsed = "${digits.trim()} (base $fb) = ${NumberTheory.parseInBase(digits, fb)}"
    } catch (e: MathError) { parseErr = e.message }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Card {
            Text("Whole number", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(n, { n = it }, label = { Text("n, e.g. 360 or 2^31 - 1") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            for ((title, r) in facts) {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                Output(r.first?.first, emptyList(), r.second)
            }
        }
        Card {
            Text("Convert n to another base", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(base, { base = it }, label = { Text("base (2–36)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Row(Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (b in listOf(2, 8, 16)) Chip(if (b == 2) "Binary" else if (b == 8) "Octal" else "Hex", { base = b.toString() }, active = base.trim() == b.toString())
            }
            Output(convOut, error = convErr)
        }
        Card {
            Text("Read digits in a base", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(digits, { digits = it }, label = { Text("digits") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(fromBase, { fromBase = it }, label = { Text("base") }, singleLine = true, modifier = Modifier.weight(0.5f))
            }
            Output(parsed?.let { listOf(it) }, error = parseErr)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================ matrices
@Composable
internal fun MatrixTab(vm: CalcViewModel) {
    var a by rememberSaveable { mutableStateOf("1 2; 3 4") }
    var b by rememberSaveable { mutableStateOf("5 6; 7 8") }
    var power by rememberSaveable { mutableStateOf("3") }
    fun show(title: String, block: () -> List<String>): Triple<String, List<String>?, String?> =
        try { Triple(title, block(), null) } catch (e: MathError) { Triple(title, null, e.message) }
    val ma = try { Matrix.parse(a) } catch (e: MathError) { null }
    val errA = if (ma == null) try { Matrix.parse(a); null } catch (e: MathError) { e.message } else null
    val mb = try { Matrix.parse(b) } catch (e: MathError) { null }
    val results = ArrayList<Triple<String, List<String>?, String?>>()
    if (ma != null) {
        results.add(show("Size, rank") { listOf("${ma.m} × ${ma.n},  rank ${ma.rank()}") })
        results.add(show("Determinant") { listOf(ma.determinant().toString()) })
        results.add(show("Trace") { listOf(ma.trace().toString()) })
        results.add(show("Transpose") { ma.transpose().toLines() })
        results.add(show("Inverse") { ma.inverse().toLines() })
        results.add(show("Row-reduced form") { ma.rref().toLines() })
        val k = power.trim().toIntOrNull()
        if (k != null) results.add(show("A^$k") { ma.pow(k).toLines() })
        if (mb != null) {
            results.add(show("A + B") { (ma + mb).toLines() })
            results.add(show("A × B") { (ma * mb).toLines() })
            results.add(show("Solve A x = B (B as a column)") { ma.solve(mb).toLines() })
        }
    }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Card {
            Text("Matrix A", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(a, { a = it }, label = { Text("Rows separated by ;  e.g. 1 2; 3 4") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            errA?.let { Text(it, color = cs.error, fontSize = 13.sp) }
            Text("Matrix B (optional)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(b, { b = it }, label = { Text("e.g. 5 6; 7 8   (fractions like 1/2 work)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            OutlinedTextField(power, { power = it }, label = { Text("power for A^n") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        for ((title, lines, err) in results) {
            Card {
                Text(title, fontSize = 12.sp, color = cs.primary)
                Output(lines, error = err, mono = true)
            }
        }
        Text("Exact fractions are used throughout, up to ${Matrix.MAX}×${Matrix.MAX}.", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================ units
@Composable
internal fun UnitsTab(vm: CalcViewModel) {
    val cs = MaterialTheme.colorScheme
    var cat by rememberSaveable { mutableStateOf(UnitCategory.LENGTH.name) }
    var value by rememberSaveable { mutableStateOf("1") }
    var from by rememberSaveable { mutableStateOf("km") }
    val category = UnitCategory.valueOf(cat)
    val units = Units.of(category)
    val fromDef = units.firstOrNull { it.symbol == from } ?: units.first()
    var err: String? = null
    var input: Rat? = null
    try {
        val v = Calculator.evalValue(value, vm.workspace, vm.toolSettings())
        input = (v as? com.powermath.math.Value.Q)?.r ?: Rat.fromDecimal(java.math.BigDecimal(com.powermath.math.Ops.toDouble(v)).toPlainString().substringBefore('.'), java.math.BigDecimal(com.powermath.math.Ops.toDouble(v)).toPlainString().substringAfter('.', ""), 0)
    } catch (e: MathError) { err = e.message } catch (e: RuntimeException) { err = "Enter a number" }
    val rows = ArrayList<Pair<String, String>>()
    if (input != null) for (u in units) {
        if (u == fromDef) continue
        try {
            val r = Units.convert(input, fromDef, u)
            val text = if (r.isInteger || r.den.toString().length <= 6) r.toString() else r.toBigDecimal(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
            rows.add("$text ${u.symbol}" to u.name)
        } catch (e: MathError) { err = e.message }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in UnitCategory.values()) Chip(c.title, { cat = c.name; from = Units.of(c).first().symbol }, active = c == category)
        }
        Card {
            OutlinedTextField(value, { value = it }, label = { Text("Value (numbers or expressions)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (u in units) Chip(u.symbol, { from = u.symbol }, active = u == fromDef, desc = u.name)
            }
            Text("From: ${fromDef.name}", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        err?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(4.dp)) }
        Card {
            for ((r, name) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(r, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(name, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
