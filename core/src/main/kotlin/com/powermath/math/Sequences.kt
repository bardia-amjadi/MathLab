package com.powermath.math

import java.math.BigInteger

/** Result of recognising a pattern in a list of numbers. */
data class SeqAnalysis(val kind: String, val lines: List<String>, val steps: List<String>)

/** Arithmetic / geometric sequences and simple pattern detection (exact, rational terms). */
object Sequences {
    private fun r(x: Rat) = x.toString()
    private fun paren(x: Rat) = if (x.isInteger && x.signum >= 0) x.toString() else "(${x})"

    // ---- closed forms on Values (used by the evaluator functions) ----
    private fun posInt(n: Value, what: String): Int {
        if (n !is Value.Q || !n.r.isInteger || n.r.signum <= 0) throw MathError(ErrorKind.DOMAIN, "$what must be a positive whole number")
        if (n.r.num.bitLength() > 30) throw MathError(ErrorKind.LIMIT, "$what is too large")
        return n.r.num.toInt()
    }

    /** aₙ = a₁ + (n − 1)d */
    fun arithTerm(a1: Value, d: Value, n: Value): Value {
        val k = posInt(n, "The term number n")
        return Ops.add(a1, Ops.mul(Value.of((k - 1).toLong()), d))
    }

    /** Sₙ = n/2 · (2a₁ + (n − 1)d) */
    fun arithSum(a1: Value, d: Value, n: Value): Value {
        val k = posInt(n, "The number of terms n")
        val inner = Ops.add(Ops.mul(Value.of(2), a1), Ops.mul(Value.of((k - 1).toLong()), d))
        return Ops.div(Ops.mul(Value.of(k.toLong()), inner), Value.of(2))
    }

    /** aₙ = a₁ · r^(n − 1) */
    fun geomTerm(a1: Value, ratio: Value, n: Value): Value {
        val k = posInt(n, "The term number n")
        return Ops.mul(a1, Ops.pow(ratio, Value.of((k - 1).toLong())))
    }

    /** Sₙ = a₁ (1 − rⁿ) / (1 − r), or n·a₁ when r = 1 */
    fun geomSum(a1: Value, ratio: Value, n: Value): Value {
        val k = posInt(n, "The number of terms n")
        if (ratio is Value.Q && ratio.r == Rat.ONE) return Ops.mul(Value.of(k.toLong()), a1)
        val num = Ops.sub(Value.ONE, Ops.pow(ratio, Value.of(k.toLong())))
        return Ops.mul(a1, Ops.div(num, Ops.sub(Value.ONE, ratio)))
    }

    /** a₁ / (1 − r) for |r| < 1 */
    fun geomInfinite(a1: Value, ratio: Value): Value {
        if (Math.abs(Ops.toDouble(ratio)) >= 1.0) throw MathError(ErrorKind.DOMAIN, "An infinite geometric series converges only when |r| < 1")
        return Ops.div(a1, Ops.sub(Value.ONE, ratio))
    }

    // ---- pattern detection ----
    private fun coef(c: Rat, v: String): String = when {
        c == Rat.ONE -> v
        c == -Rat.ONE -> "-$v"
        c.isInteger -> "$c$v"
        else -> "(${c})$v"
    }

    /** Builds "A n² + B n + C" skipping zero terms. */
    private fun poly(a: Rat, b: Rat, c: Rat): String {
        val parts = ArrayList<Pair<Boolean, String>>()   // (negative, magnitude text)
        fun add(co: Rat, v: String) {
            if (co.isZero) return
            val mag = co.abs()
            val body = if (v.isEmpty()) r(mag) else if (mag == Rat.ONE) v else coef(mag, v)
            parts.add(Pair(co.signum < 0, body))
        }
        add(a, "n²"); add(b, "n"); add(c, "")
        if (parts.isEmpty()) return "0"
        val sb = StringBuilder()
        parts.forEachIndexed { i, (neg, body) ->
            if (i == 0) sb.append(if (neg) "-$body" else body) else sb.append(if (neg) " - $body" else " + $body")
        }
        return sb.toString()
    }

    fun analyze(terms: List<Rat>, nextCount: Int = 3): SeqAnalysis {
        if (terms.size < 3) throw MathError(ErrorKind.SYNTAX, "Give at least 3 terms, e.g. seq(2, 6, 18, 54)")
        if (terms.size > 200) throw MathError(ErrorKind.LIMIT, "Too many terms (max 200)")
        val n = terms.size
        val diffs = (1 until n).map { terms[it] - terms[it - 1] }
        val lines = ArrayList<String>()
        val steps = ArrayList<String>()
        fun nextList(f: (Int) -> Rat) = (n + 1..n + nextCount).joinToString(", ") { r(f(it)) }

        // arithmetic
        if (diffs.all { it == diffs[0] }) {
            val d = diffs[0]; val a1 = terms[0]
            lines += "Arithmetic sequence, common difference d = ${r(d)}"
            lines += "aₙ = ${r(a1)} + (n − 1)·${paren(d)} = ${poly(Rat.ZERO, d, a1 - d)}"
            lines += "Next terms: ${nextList { k -> a1 + Rat.of(k - 1) * d }}"
            lines += "Sₙ = n(2·${paren(a1)} + (n − 1)·${paren(d)})/2"
            val sum = terms.fold(Rat.ZERO) { s, x -> s + x }
            lines += "Sum of the $n given terms = ${r(sum)}"
            steps += "Each term differs from the previous one by the same amount: ${r(d)}"
            steps += "So aₙ = a₁ + (n − 1)d"
            return SeqAnalysis("arithmetic", lines, steps)
        }
        // geometric
        if (terms.none { it.isZero }) {
            val ratios = (1 until n).map { terms[it] / terms[it - 1] }
            if (ratios.all { it == ratios[0] }) {
                val q = ratios[0]; val a1 = terms[0]
                lines += "Geometric sequence, common ratio r = ${r(q)}"
                lines += "aₙ = ${r(a1)}·${paren(q)}^(n − 1)"
                lines += "Next terms: ${nextList { k -> a1 * q.pow(k - 1) }}"
                lines += "Sₙ = ${r(a1)}·(1 − ${paren(q)}ⁿ)/(1 − ${paren(q)})".let { if (q == Rat.ONE) "Sₙ = n·${r(a1)}" else it }
                val sum = terms.fold(Rat.ZERO) { s, x -> s + x }
                lines += "Sum of the $n given terms = ${r(sum)}"
                if (q.abs() < Rat.ONE) lines += "Infinite sum S∞ = ${r(a1 / (Rat.ONE - q))}"
                else lines += "The infinite series diverges because |r| ≥ 1"
                steps += "Each term is the previous one multiplied by the same number: ${r(q)}"
                steps += "So aₙ = a₁ · r^(n − 1)"
                return SeqAnalysis("geometric", lines, steps)
            }
        }
        // quadratic (constant second difference)
        if (n >= 4) {
            val second = (1 until diffs.size).map { diffs[it] - diffs[it - 1] }
            if (second.all { it == second[0] } && !second[0].isZero) {
                val c2 = second[0]; val a1 = terms[0]; val d1 = diffs[0]
                val A = c2 / Rat.of(2); val B = d1 - c2 * Rat.of(3, 2); val C = a1 - d1 + c2
                lines += "Quadratic pattern: the second differences are constant (${r(c2)})"
                lines += "aₙ = ${poly(A, B, C)}"
                lines += "Next terms: ${nextList { k -> A * Rat.of(k * k) + B * Rat.of(k) + C }}"
                steps += "First differences: ${diffs.joinToString(", ") { r(it) }}"
                steps += "Second differences are all ${r(c2)}, so aₙ is a quadratic in n"
                return SeqAnalysis("quadratic", lines, steps)
            }
            // Fibonacci-like
            if ((2 until n).all { terms[it] == terms[it - 1] + terms[it - 2] }) {
                val a = ArrayList(terms)
                repeat(nextCount) { a.add(a[a.size - 1] + a[a.size - 2]) }
                lines += "Fibonacci-type pattern: each term is the sum of the two before it"
                lines += "Next terms: ${a.drop(n).joinToString(", ") { r(it) }}"
                steps += "aₙ = aₙ₋₁ + aₙ₋₂"
                return SeqAnalysis("fibonacci", lines, steps)
            }
        }
        lines += "No simple arithmetic, geometric, quadratic or Fibonacci pattern found"
        steps += "First differences: ${diffs.joinToString(", ") { r(it) }}"
        return SeqAnalysis("unknown", lines, steps)
    }
}
