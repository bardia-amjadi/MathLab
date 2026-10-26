package com.powermath.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.powermath.math.OcrCleanup
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen
import com.powermath.viewmodel.ScanState
import java.io.File

/** Take or pick a photo of an expression, read it on the device, let the user check it, then use it. */
@Composable
fun ScanHost(vm: CalcViewModel) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun recognise(uri: Uri) {
        vm.scan = ScanState.Working
        try {
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result ->
                    val raw = result.text
                    val cleaned = OcrCleanup.toExpression(raw)
                    vm.scan = if (cleaned.isBlank()) ScanState.Failed("No expression was found. Try a closer, brighter, straighter photo.")
                    else ScanState.Result(raw, cleaned)
                }
                .addOnFailureListener { e -> vm.scan = ScanState.Failed("Could not read the photo: ${e.message ?: "unknown problem"}") }
        } catch (e: Exception) {
            vm.scan = ScanState.Failed("Could not open the photo: ${e.message ?: "unknown problem"}")
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) recognise(uri) else vm.scan = ScanState.None
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognise(uri) else vm.scan = ScanState.None
    }

    when (val st = vm.scan) {
        ScanState.None -> {}
        ScanState.Choose -> AlertDialog(
            onDismissRequest = { vm.scan = ScanState.None },
            title = { Text("Scan an expression") },
            text = { Text("Take a photo of a printed or neatly handwritten expression, or choose one from your gallery. The photo is read on your phone and never leaves it.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val dir = File(context.cacheDir, "images").apply { mkdirs() }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "scan.jpg"))
                        photoUri = uri
                        takePicture.launch(uri)
                    } catch (e: Exception) { vm.scan = ScanState.Failed("The camera could not be opened: ${e.message ?: ""}") }
                }) { Text("Take photo") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose from gallery") }
                    TextButton(onClick = { vm.scan = ScanState.None }) { Text("Cancel") }
                }
            }
        )
        ScanState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading the photo…") },
            text = { CircularProgressIndicator(Modifier.padding(8.dp)) },
            confirmButton = {}
        )
        is ScanState.Failed -> AlertDialog(
            onDismissRequest = { vm.scan = ScanState.None },
            title = { Text("Nothing to use") },
            text = { Text(st.message) },
            confirmButton = { TextButton(onClick = { vm.scan = ScanState.Choose }) { Text("Try again") } },
            dismissButton = { TextButton(onClick = { vm.scan = ScanState.None }) { Text("Close") } }
        )
        is ScanState.Result -> {
            var text by remember(st) { mutableStateOf(st.cleaned) }
            AlertDialog(
                onDismissRequest = { vm.scan = ScanState.None },
                title = { Text("Check the expression") },
                text = {
                    Column {
                        Text("Fix any mistakes before using it. Fractions written on two lines and handwriting may not be read correctly.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Expression") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = { val t = text.trim(); vm.scan = ScanState.None; vm.setExpression(t); vm.screen = Screen.CALCULATOR }) { Text("Use") }
                },
                dismissButton = {
                    Column {
                        TextButton(onClick = { val t = text.trim(); vm.scan = ScanState.None; vm.runInput(t) }) { Text("Use and solve") }
                        TextButton(onClick = { vm.scan = ScanState.None }) { Text("Cancel") }
                    }
                }
            )
        }
    }
}
