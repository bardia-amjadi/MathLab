package com.powermath.math

/** Turns text read from a photo into input the calculator understands. Pure text processing, fully testable. */
object OcrCleanup {
    private val superscripts = mapOf('⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4', '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9')

    fun toExpression(raw: String): String {
        val lines = raw.replace('\r', '\n').split('\n').map { cleanLine(it) }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        // several equations (a system) are joined with ';', anything else with a space
        return if (lines.size > 1 && lines.all { '=' in it && !it.contains("<") && !it.contains(">") }) lines.joinToString("; ") else lines.joinToString(" ")
    }

    private fun cleanLine(line: String): String {
        var s = line.trim()
        // look-alike characters
        s = s.replace('×', '*').replace('·', '*').replace('⋅', '*').replace('∙', '*').replace('✕', '*')
            .replace('÷', '/').replace('∕', '/').replace('⁄', '/')
            .replace('−', '-').replace('–', '-').replace('—', '-').replace('‐', '-').replace('﹣', '-')
            .replace('＋', '+').replace('＝', '=').replace('（', '(').replace('）', ')')
            .replace("≤", "<=").replace("≥", ">=").replace("≠", "!=").replace("⩽", "<=").replace("⩾", ">=")
            .replace("π", "pi")
            .replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')')
            .replace(" ", " ")
        if (':' in s && '=' !in s) s = s.replace(':', '/')
        // superscript digits: x² -> x^2
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c in superscripts) {
                sb.append('^')
                while (i < s.length && s[i] in superscripts) { sb.append(superscripts[s[i]]); i++ }
                continue
            }
            sb.append(c); i++
        }
        s = sb.toString()
        // square roots: √9 -> sqrt(9), √(x+1) -> sqrt(x+1), √x -> sqrt(x)
        s = Regex("√\\s*\\(").replace(s, "sqrt(")
        s = Regex("√\\s*([0-9.]+|[A-Za-z])").replace(s) { "sqrt(${it.groupValues[1]})" }
        s = s.replace("√", "sqrt")
        // digits misread as letters, only when surrounded by digits
        s = Regex("(?<=\\d)[Oo](?=\\d)").replace(s, "0")
        s = Regex("(?<=\\d)[lI|](?=\\d)").replace(s, "1")
        s = Regex("(?<=\\d)[Ss](?=\\d)").replace(s, "5")
        // "3 x 4" with spaces on both sides between numbers is a product
        s = Regex("(?<=\\d)\\s+[xX]\\s+(?=\\d)").replace(s, " * ")
        // trailing "=" or "?" left over from "2+3=?"
        s = s.trim().trimEnd('?', '.', ',').trim()
        if (s.endsWith("=")) s = s.dropLast(1).trim()
        // implicit exponent marker such as x^ 2
        s = Regex("\\^\\s+").replace(s, "^")
        // keep only characters the calculator can read
        s = s.filter { it.isLetterOrDigit() && it.code < 128 || it in " +-*/^=<>!().,;_%" }
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
