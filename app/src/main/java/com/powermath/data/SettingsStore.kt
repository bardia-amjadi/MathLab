package com.powermath.data

import android.content.Context
import com.powermath.math.AngleMode
import com.powermath.math.FormatSettings
import com.powermath.math.ResultMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemePref(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

/** Accent colour of the whole app. */
enum class AccentPref(val label: String) { YELLOW("Yellow"), RED("Red"), BLUE("Blue"), GREEN("Green"), PURPLE("Purple") }

/** Size presets for the keypad and the expression text. */
/** Which page opens after the intro. */
enum class StartPage(val label: String) { HOME("Home"), CALCULATOR("Calculator") }

/** Font used for the expression and results. */
enum class FontPref(val label: String) { DEFAULT("Default"), MONO("Monospace"), SERIF("Serif") }

enum class SizePref(val label: String, val scale: Float) { SMALL("Small", 0.85f), NORMAL("Normal", 1f), LARGE("Large", 1.2f) }

data class AppSettings(
    val mode: ResultMode = ResultMode.EXACT,
    val decimals: Int = 10,
    val sigDigits: Int = 10,
    val angle: AngleMode = AngleMode.DEG,
    val theme: ThemePref = ThemePref.DARK,
    val haptics: Boolean = true,
    val accent: AccentPref = AccentPref.YELLOW,
    val keypadSize: SizePref = SizePref.NORMAL,
    val textSize: SizePref = SizePref.NORMAL,
    val startPage: StartPage = StartPage.HOME,
    val showIntro: Boolean = true,
    val animations: Boolean = true,
    val livePreview: Boolean = true,
    val autoSteps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val font: FontPref = FontPref.DEFAULT
) {
    val format: FormatSettings get() = FormatSettings(mode, decimals, sigDigits)
}

/** Settings persisted in SharedPreferences and exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("powermath_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _state

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, default.name) ?: default.name) }.getOrDefault(default)

    private fun load() = AppSettings(
        mode = enumOf("mode", ResultMode.EXACT),
        decimals = prefs.getInt("decimals", 10).coerceIn(0, 50),
        sigDigits = prefs.getInt("sig", 10).coerceIn(1, 50),
        angle = enumOf("angle", AngleMode.DEG),
        theme = enumOf("theme", ThemePref.DARK),
        haptics = prefs.getBoolean("haptics", true),
        accent = enumOf("accent", AccentPref.YELLOW),
        keypadSize = enumOf("keypadSize", SizePref.NORMAL),
        textSize = enumOf("textSize", SizePref.NORMAL),
        startPage = enumOf("startPage", StartPage.HOME),
        showIntro = prefs.getBoolean("showIntro", true),
        animations = prefs.getBoolean("animations", true),
        livePreview = prefs.getBoolean("livePreview", true),
        autoSteps = prefs.getBoolean("autoSteps", false),
        keepScreenOn = prefs.getBoolean("keepScreenOn", false),
        font = enumOf("font", FontPref.DEFAULT)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        _state.value = s
        prefs.edit()
            .putString("mode", s.mode.name).putInt("decimals", s.decimals).putInt("sig", s.sigDigits)
            .putString("angle", s.angle.name).putString("theme", s.theme.name).putBoolean("haptics", s.haptics)
            .putString("accent", s.accent.name).putString("keypadSize", s.keypadSize.name).putString("textSize", s.textSize.name)
            .putString("startPage", s.startPage.name).putBoolean("showIntro", s.showIntro).putBoolean("animations", s.animations)
            .putBoolean("livePreview", s.livePreview).putBoolean("autoSteps", s.autoSteps).putBoolean("keepScreenOn", s.keepScreenOn)
            .putString("font", s.font.name)
            .apply()
    }

    /** Crash report saved by the uncaught-exception handler; shown once on the next launch. */
    fun saveCrash(text: String) { prefs.edit().putString("last_crash", text.take(6000)).commit() }
    fun takeCrash(): String? = prefs.getString("last_crash", null)?.also { prefs.edit().remove("last_crash").apply() }
}
