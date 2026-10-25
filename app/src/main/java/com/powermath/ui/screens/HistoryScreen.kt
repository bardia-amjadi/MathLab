package com.powermath.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.DialogRequest
import com.powermath.viewmodel.Screen
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: CalcViewModel, onCopy: (String) -> Unit) {
    val entries by vm.history.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val dateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("History", onBack = { vm.back() }) {
            if (entries.isNotEmpty()) Chip("Clear all", { vm.dialog = DialogRequest.ConfirmClearHistory })
        }
        if (entries.isEmpty()) {
            Text("No calculations yet. Results you evaluate are saved here, on this device only.",
                color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
        }
        // LazyColumn only composes visible rows, and the query is capped, so long histories stay fast.
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { e ->
                Surface(shape = RoundedCornerShape(14.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()
                    .clickable { vm.restoreFromHistory(e) }
                    .semantics { contentDescription = "Restore ${e.expression}" }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.expression, fontSize = 16.sp, color = cs.onSurfaceVariant, maxLines = 2)
                        Text(e.result, fontSize = 22.sp, maxLines = 6)
                        Text("${e.mode.lowercase().replaceFirstChar { it.uppercase() }} · ${dateFmt.format(Date(e.timestamp))}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { vm.restoreFromHistory(e) }) { Text("Restore") }
                            TextButton(onClick = { onCopy(e.result); vm.toast = "Result copied" }) { Text("Copy result") }
                            TextButton(onClick = { vm.deleteHistory(e.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
