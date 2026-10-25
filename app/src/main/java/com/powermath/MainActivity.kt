package com.powermath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.powermath.data.SettingsStore
import com.powermath.ui.PowerMathApp
import com.powermath.viewmodel.CalcViewModel
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Save any crash so the next launch can show what went wrong (the user can copy it and report it).
        val store = SettingsStore(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { store.saveCrash(StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()) }
            previous?.uncaughtException(t, e)
        }
        vm.lastCrash = store.takeCrash()
        enableEdgeToEdge()
        setContent { PowerMathApp(vm) }
    }
}
