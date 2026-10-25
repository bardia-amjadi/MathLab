package com.powermath.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.math.Formatter
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.DialogRequest
import com.powermath.viewmodel.Screen

@Composable
fun WorkspaceScreen(vm: CalcViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Workspace", onBack = { vm.back() })
        vm.toast?.let { Text(it, color = cs.primary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Variables", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Chip("+ Add variable", { vm.dialog = DialogRequest.DefineVar() })
                }
            }
            if (vm.wsVars.isEmpty()) item { Text("No variables yet. Try  a := 5  in the calculator.", color = cs.onSurfaceVariant, fontSize = 13.sp) }
            items(vm.wsVars, key = { "v_" + it.name }) { v ->
                Surface(shape = RoundedCornerShape(14.dp), color = cs.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${v.name} = ${Formatter.format(v.value, settings.format)}", fontSize = 18.sp)
                        if (v.source != Formatter.format(v.value, settings.format)) Text("defined as: ${v.source}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { vm.insert(v.name); vm.screen = Screen.CALCULATOR }) { Text("Insert") }
                            TextButton(onClick = { vm.dialog = DialogRequest.DefineVar(v.name, v.source, true) }) { Text("Edit") }
                            TextButton(onClick = { vm.toast = vm.deleteVariable(v.name) ?: "Deleted ${v.name}" }) { Text("Delete") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Functions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Chip("+ Add function", { vm.dialog = DialogRequest.DefineFn() })
                }
            }
            if (vm.wsFuncs.isEmpty()) item { Text("No functions yet. Try  f(x) = x^2 + 1  in the calculator.", color = cs.onSurfaceVariant, fontSize = 13.sp) }
            items(vm.wsFuncs, key = { "f_" + it.name }) { f ->
                Surface(shape = RoundedCornerShape(14.dp), color = cs.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${f.name}(${f.params.joinToString(", ")}) = ${f.source}", fontSize = 18.sp)
                        Row {
                            TextButton(onClick = { vm.insert("${f.name}("); vm.screen = Screen.CALCULATOR }) { Text("Insert") }
                            TextButton(onClick = { vm.openGraph(); vm.plotSaved(f) }) { Text("Graph") }
                            TextButton(onClick = { vm.dialog = DialogRequest.DefineFn(f.name, f.params.joinToString(", "), f.source, true) }) { Text("Edit") }
                            TextButton(onClick = { vm.toast = vm.deleteFunction(f.name) ?: "Deleted ${f.name}" }) { Text("Delete") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
}
