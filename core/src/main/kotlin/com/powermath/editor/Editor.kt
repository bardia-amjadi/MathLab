package com.powermath.editor

import com.powermath.math.Names

data class EditorState(val text: String = "", val cursor: Int = 0) {
    companion object { fun of(text: String) = EditorState(text, text.length) }
}

/** Pure text-editing operations for the calculator expression editor. */
object Editor {
    private val funcBefore = Regex("([A-Za-z_]{2,})\\($")
    private val numberBefore = Regex("[0-9.]+(E[+-]?[0-9]+)?$")
    private val wordParenBefore = Regex("[A-Za-z_]+\\($")
    private val wordBefore = Regex("[A-Za-z_]+$")
    private val negNumber = Regex("\\(-([0-9.]+)\\)$")

    fun insert(s: EditorState, str: String): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        return EditorState(s.text.substring(0, c) + str + s.text.substring(c), c + str.length)
    }

    private fun cut(s: EditorState, n: Int): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        val k = n.coerceAtMost(c)
        return EditorState(s.text.substring(0, c - k) + s.text.substring(c), c - k)
    }

    fun backspace(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        if (c == 0) return s
        val before = s.text.substring(0, c)
        val m = funcBefore.find(before)
        if (m != null) {
            val name = m.groupValues[1]
            if (name in Names.functions || name in Names.commands) return cut(s, name.length + 1)
        }
        for (two in listOf("<=", ">=", "!=", ":=")) if (before.endsWith(two)) return cut(s, 2)
        return cut(s, 1)
    }

    /** Clear entry: remove the last number, name, function token or operator before the cursor. */
    fun clearEntry(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        if (c == 0) return s
        val before = s.text.substring(0, c)
        for (r in listOf(numberBefore, wordParenBefore, wordBefore)) {
            val m = r.find(before)
            if (m != null) return cut(s, m.value.length)
        }
        for (two in listOf("<=", ">=", "!=", ":=")) if (before.endsWith(two)) return cut(s, 2)
        return cut(s, 1)
    }

    fun clearAll(): EditorState = EditorState("", 0)
    /** Moving across the ")/(" between a fraction's numerator and denominator takes one step. */
    fun left(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        val step = if (s.text.substring(0, c).endsWith(")/(")) 3 else 1
        return s.copy(cursor = (c - step).coerceAtLeast(0))
    }
    fun right(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        val step = if (s.text.substring(c).startsWith(")/(")) 3 else 1
        return s.copy(cursor = (c + step).coerceAtMost(s.text.length))
    }

    private val numeratorBefore = Regex("([0-9.]+|[A-Za-z_]+|\\([^()]*\\))$")

    /**
     * The fraction key. With a number, name or (group) just before the cursor it becomes the numerator and the cursor
     * moves into the denominator: "3" -> "(3)/(|)". With nothing before it inserts an empty "(|)/()" template.
     */
    fun fraction(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        val before = s.text.substring(0, c)
        val after = s.text.substring(c)
        val m = numeratorBefore.find(before)
        val isFunctionCall = m != null && m.value.startsWith("(") && Regex("[A-Za-z_]$").containsMatchIn(before.substring(0, m.range.first))
        if (m == null || isFunctionCall || (m.value.all { it.isLetter() || it == '_' } && m.value in Names.functions)) {
            val t = before + "()/()" + after
            return EditorState(t, before.length + 1)
        }
        val prefix = before.substring(0, m.range.first)
        val num = if (m.value.startsWith("(")) m.value else "(${m.value})"
        val t = prefix + num + "/()" + after
        return EditorState(t, prefix.length + num.length + 2)
    }

    /** The (-) key: toggles the sign of the number before the cursor, otherwise inserts a unary minus. */
    fun negate(s: EditorState): EditorState {
        val c = s.cursor.coerceIn(0, s.text.length)
        val before = s.text.substring(0, c)
        val after = s.text.substring(c)
        negNumber.find(before)?.let { m ->
            val nb = before.substring(0, m.range.first) + m.groupValues[1]
            return EditorState(nb + after, nb.length)
        }
        numberBefore.find(before)?.let { m ->
            val nb = before.substring(0, m.range.first) + "(-" + m.value + ")"
            return EditorState(nb + after, nb.length)
        }
        return insert(s, "-")
    }
}

/** Display-only prettifier for the live preview line (the AST, not this, is used for calculation). */
object Pretty {
    private val sup = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻')

    fun format(src: String): String {
        var s = src
        s = Regex("([0-9.])E([+-]?[0-9]+)").replace(s) { "${it.groupValues[1]}×10^${it.groupValues[2].removePrefix("+")}" }
        s = s.replace("sqrt(", "√(")
        s = Regex("(?<![A-Za-z_])pi(?![A-Za-z_])").replace(s, "π")
        s = Regex("\\^(-?[0-9]+)").replace(s) { m -> m.groupValues[1].map { sup[it] ?: it }.joinToString("") }
        s = s.replace("<=", "≤").replace(">=", "≥").replace("!=", "≠")
        s = Regex("\\(([0-9A-Za-z_.]+)\\)/\\(([0-9A-Za-z_.]+)\\)").replace(s) { "${it.groupValues[1]}⁄${it.groupValues[2]}" }
        s = s.replace('*', '×').replace('/', '÷').replace('-', '−')
        return s
    }
}
