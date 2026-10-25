package com.powermath.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermath.math.Formatter
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.DialogRequest
import com.powermath.viewmodel.PickPurpose
import com.powermath.viewmodel.ToolKind

@Composable
fun DialogHost(vm: CalcViewModel) {
    when (val d = vm.dialog) {
        null -> {}
        is DialogRequest.DefineVar -> DefineVarDialog(d, vm)
        is DialogRequest.DefineFn -> DefineFnDialog(d, vm)
        is DialogRequest.Pick -> PickDialog(d.purpose, vm)
        is DialogRequest.Tool -> ToolDialog(d, vm)
        DialogRequest.ConfirmClearHistory -> AlertDialog(
            onDismissRequest = { vm.dialog = null },
            title = { Text("Clear all history?") },
            text = { Text("All saved calculations will be deleted. Variables and functions are not affected.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); vm.dialog = null }) { Text("Clear all") } },
            dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DefineVarDialog(d: DialogRequest.DefineVar, vm: CalcViewModel) {
    var name by remember { mutableStateOf(d.name) }
    var expr by remember { mutableStateOf(d.expr) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(if (d.editing) "Edit variable" else "Define variable") },
        text = {
            Column {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true, enabled = !d.editing,
                    supportingText = { Text("Letters and underscores, e.g. a, rate") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(expr, { expr = it; error = null }, label = { Text("Value") }, singleLine = true,
                    supportingText = { Text("A number or expression, e.g. 5 or sqrt(2)") })
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { val e = vm.defineVariable(name, expr, d.editing); if (e == null) vm.dialog = null else error = e }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } }
    )
}

@Composable
private fun DefineFnDialog(d: DialogRequest.DefineFn, vm: CalcViewModel) {
    var name by remember { mutableStateOf(d.name) }
    var params by remember { mutableStateOf(d.params) }
    var body by remember { mutableStateOf(d.body) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(if (d.editing) "Edit function" else "Define function") },
        text = {
            Column {
                Row {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true, enabled = !d.editing, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(params, { params = it; error = null }, label = { Text("Parameters") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it; error = null }, label = { Text("Body") }, singleLine = true,
                    supportingText = { Text("e.g. f(x) = x^2 + 1  →  name f, parameter x, body x^2 + 1") })
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { val e = vm.defineFunction(name, params, body, d.editing); if (e == null) vm.dialog = null else error = e }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } }
    )
}

@Composable
private fun PickDialog(purpose: PickPurpose, vm: CalcViewModel) {
    val vars = vm.wsVars
    val funcs = vm.wsFuncs
    val fmt = vm.settings.value.format
    data class Item(val label: String, val detail: String, val onPick: () -> Unit)
    val entries: List<Item> = when (purpose) {
        PickPurpose.EDIT_VAR -> vars.map { v -> Item(v.name, v.source) { vm.dialog = DialogRequest.DefineVar(v.name, v.source, true) } }
        PickPurpose.DELETE_VAR -> vars.map { v -> Item(v.name, "= ${Formatter.format(v.value, fmt)}") { vm.toast = vm.deleteVariable(v.name) ?: "Deleted ${v.name}"; vm.dialog = null } }
        PickPurpose.EDIT_FN -> funcs.map { f -> Item("${f.name}(${f.params.joinToString(", ")})", f.source) { vm.dialog = DialogRequest.DefineFn(f.name, f.params.joinToString(", "), f.source, true) } }
        PickPurpose.DELETE_FN -> funcs.map { f -> Item("${f.name}(${f.params.joinToString(", ")})", f.source) { vm.toast = vm.deleteFunction(f.name) ?: "Deleted ${f.name}"; vm.dialog = null } }
        PickPurpose.INSERT ->
            vars.map { v -> Item(v.name, "= ${Formatter.format(v.value, fmt)}") { vm.insert(v.name); vm.dialog = null } } +
                funcs.map { f -> Item("${f.name}(${f.params.joinToString(", ")})", f.source) { vm.insert("${f.name}("); vm.dialog = null } }
    }
    val title = when (purpose) {
        PickPurpose.EDIT_VAR -> "Edit which variable?"; PickPurpose.DELETE_VAR -> "Delete which variable?"
        PickPurpose.EDIT_FN -> "Edit which function?"; PickPurpose.DELETE_FN -> "Delete which function?"
        PickPurpose.INSERT -> "Insert saved item"
    }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(title) },
        text = {
            if (entries.isEmpty()) Text("Nothing saved yet. Use Def var / Def fn, or type a := 5 or f(x) = x^2 in the calculator.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(entries) { entry ->
                    TextButton(onClick = entry.onPick, modifier = Modifier.fillMaxWidth()) {
                        Text(entry.label, modifier = Modifier.weight(1f))
                        Text(entry.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Close") } }
    )
}

@Composable
private fun ToolDialog(d: DialogRequest.Tool, vm: CalcViewModel) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(d.prefill) }
    val run: (String) -> Unit = { cmd -> vm.dialog = null; vm.setExpression(cmd); vm.evaluate(cmd) }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text(d.tool.title) },
        text = {
            Column {
                when (d.tool) {
                    ToolKind.EQUATION -> OutlinedTextField(text, { text = it }, label = { Text("Equation") }, singleLine = true,
                        supportingText = { Text("e.g. x^2 - 5x + 6 = 0") })
                    ToolKind.INEQUALITY -> OutlinedTextField(text, { text = it }, label = { Text("Inequality") }, singleLine = true,
                        supportingText = { Text("e.g. x^2 - 4 < 0  (use <, >, <=, >=, !=)") })
                    ToolKind.QUADRATIC -> {
                        Text("a·x² + b·x + c = 0", fontSize = 14.sp)
                        Row(Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(a, { a = it }, label = { Text("a") }, singleLine = true, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(b, { b = it }, label = { Text("b") }, singleLine = true, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(c, { c = it }, label = { Text("c") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    ToolKind.SYSTEM -> OutlinedTextField(text, { text = it }, label = { Text("One equation per line") }, minLines = 3, maxLines = 5,
                        supportingText = { Text("e.g.\nx + y = 3\nx - y = 1") })
                    ToolKind.EVALUATE -> {
                        OutlinedTextField(text, { text = it }, label = { Text("Expression") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(b, { b = it }, label = { Text("Conditions") }, singleLine = true, supportingText = { Text("e.g. x = 2, y = 3") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (d.tool) {
                    ToolKind.EQUATION, ToolKind.INEQUALITY -> if (text.isNotBlank()) run("solve(${text.trim()})")
                    ToolKind.QUADRATIC -> run("quad(${a.ifBlank { "1" }}, ${b.ifBlank { "0" }}, ${c.ifBlank { "0" }})")
                    ToolKind.SYSTEM -> {
                        val eqs = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (eqs.isNotEmpty()) run(eqs.joinToString("; "))
                    }
                    ToolKind.EVALUATE -> if (text.isNotBlank()) run(if (b.isBlank()) "eval(${text.trim()})" else "eval(${text.trim()}; ${b.trim()})")
                }
            }) { Text(if (d.tool == ToolKind.EVALUATE) "Evaluate" else "Solve") }
        },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } }
    )
}
