package com.powermath.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

enum class ResultMode(val label: String) { EXACT("Exact"), DECIMAL("Decimal"), SCIENTIFIC("Sci") }

data class FormatSettings(
    val mode: ResultMode = ResultMode.EXACT,
    val decimals: Int = 10,
    val sigDigits: Int = 10
)

object Formatter {
    private val PI = BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899")

    fun format(v: Value, s: FormatSettings): String = when (s.mode) {
        ResultMode.EXACT -> exact(v, s)
        ResultMode.DECIMAL -> decimal(v, s.decimals)
        ResultMode.SCIENTIFIC -> scientific(v, s.sigDigits)
    }

    // ---------- exact ----------
    fun exact(v: Value, s: FormatSettings = FormatSettings()): String = when (v) {
        is Value.Q -> v.r.toString()
        is Value.S -> exactSurd(v)
        is Value.P -> exactPi(v.c)
        is Value.D -> if (v.tag == "e") "e" else decimal(v, s.decimals)
    }

    private fun lcm(a: BigInteger, b: BigInteger) = a.multiply(b).divide(a.gcd(b))

    private fun exactSurd(v: Value.S): String {
        val d = lcm(v.a.den, v.b.den)
        val A = v.a.num.multiply(d.divide(v.a.den))
        val B = v.b.num.multiply(d.divide(v.b.den))
        val radical = "√${v.n}"
        fun bTerm(b: BigInteger, first: Boolean): String {
            val mag = b.abs()
            val body = if (mag == BigInteger.ONE) radical else "$mag$radical"
            return if (first) (if (b.signum() < 0) "-$body" else body)
            else (if (b.signum() < 0) " - $body" else " + $body")
        }
        val numerator: String
        val single: Boolean
        if (A.signum() == 0) { numerator = bTerm(B, true); single = true }
        else { numerator = A.toString() + bTerm(B, false); single = false }
        return if (d == BigInteger.ONE) numerator
        else if (single) "$numerator/$d" else "($numerator)/$d"
    }

    private fun exactPi(c: Rat): String {
        val n = c.num
        val head = when {
            n == BigInteger.ONE -> "π"
            n == BigInteger.ONE.negate() -> "-π"
            else -> "${n}π"
        }
        return if (c.isInteger) head else "$head/${c.den}"
    }

    // ---------- decimal ----------
    fun toBigDecimal(v: Value, mc: MathContext): BigDecimal = when (v) {
        is Value.Q -> v.r.toBigDecimal(mc)
        is Value.S -> v.a.toBigDecimal(mc).add(v.b.toBigDecimal(mc).multiply(NumUtil.sqrtBD(BigDecimal(v.n), mc)), mc)
        is Value.P -> v.c.toBigDecimal(mc).multiply(PI, mc)
        is Value.D -> BigDecimal(v.d)
    }

    /** Exact terminating decimal for rationals whose denominator has only factors 2 and 5. */
    private fun terminating(r: Rat): Boolean {
        var d = r.den
        val two = BigInteger.valueOf(2); val five = BigInteger.valueOf(5)
        while (d.mod(two).signum() == 0) d = d.divide(two)
        while (d.mod(five).signum() == 0) d = d.divide(five)
        return d == BigInteger.ONE
    }

    fun decimal(v: Value, decimals: Int): String {
        val dec = decimals.coerceIn(0, 100)
        if (v is Value.Q) {
            val r = v.r
            if (r.isInteger) return r.num.toString()
            val rounded = BigDecimal(r.num).divide(BigDecimal(r.den), dec, RoundingMode.HALF_UP)
            if (rounded.signum() == 0) return scientific(v, maxOf(dec, 6))
            val exactFlag = terminating(r) && rounded.compareTo(BigDecimal(r.num).divide(BigDecimal(r.den))) == 0
            return (if (exactFlag) "" else "≈ ") + plain(rounded)
        }
        val d = Ops.toDouble(v)
        if (v is Value.D && (d.isNaN() || d.isInfinite())) return "undefined"
        if (Math.abs(d) >= 1e21 || (d != 0.0 && Math.abs(d) < Math.pow(10.0, -dec.toDouble()))) return scientific(v, maxOf(dec, 6))
        val bd = if (v is Value.D) BigDecimal(d).round(MathContext(15)) else toBigDecimal(v, MathContext(dec + 30))
        val rounded = bd.setScale(dec, RoundingMode.HALF_UP)
        if (rounded.signum() == 0 && d != 0.0) return scientific(v, maxOf(dec, 6))
        return "≈ " + plain(rounded)
    }

    private fun plain(bd: BigDecimal): String {
        val s = bd.stripTrailingZeros()
        return if (s.signum() == 0) "0" else s.toPlainString()
    }

    // ---------- scientific ----------
    fun scientific(v: Value, sig: Int): String {
        val digits = sig.coerceIn(1, 100)
        if (v is Value.Q && v.r.isZero) return "0"
        val mc = MathContext(digits + 20)
        val bd = toBigDecimal(v, mc)
        if (bd.signum() == 0) return "0"
        val rounded = bd.round(MathContext(digits, RoundingMode.HALF_UP))
        val exp10 = rounded.precision() - rounded.scale() - 1
        val mantissa = rounded.movePointLeft(exp10).stripTrailingZeros().toPlainString()
        var approx = true
        if (v is Value.Q) {
            if (terminating(v.r)) {
                val exactBd = BigDecimal(v.r.num).divide(BigDecimal(v.r.den))
                approx = rounded.compareTo(exactBd) != 0
            }
        }
        return (if (approx) "≈ " else "") + "$mantissa × 10^$exp10"
    }

    // ---------- parseable linear form (for persistence) ----------
    fun linear(v: Value): String = when (v) {
        is Value.Q -> if (v.r.isInteger) (if (v.r.signum < 0) "(${v.r})" else v.r.toString()) else "(${v.r})"
        is Value.S -> "(${linear(Value.Q(v.a))}+${linear(Value.Q(v.b))}*sqrt(${v.n}))"
        is Value.P -> "(${linear(Value.Q(v.c))}*pi)"
        is Value.D -> if (v.tag == "e") "e" else if (v.d < 0) "(${BigDecimal(v.d).round(MathContext(17))})" else BigDecimal(v.d).round(MathContext(17)).toString()
    }
}

sealed class Line {
    data class Text(val text: String) : Line()
    data class Val(val value: Value) : Line()
    data class Eq(val left: String, val value: Value, val op: String = "=") : Line()
    data class Iv(val lo: Value?, val loClosed: Boolean, val hi: Value?, val hiClosed: Boolean)
    data class IntervalSet(val variable: String, val intervals: List<Iv>) : Line()
}

enum class OutcomeKind { VALUE, SOLUTION, ALGEBRA, DEFINED, BOOLEAN }

data class Outcome(
    val kind: OutcomeKind,
    val lines: List<Line>,
    val steps: List<String> = emptyList(),
    val value: Value? = null,
    val graphExpr: String? = null,
    val changes: List<WsChange> = emptyList()
) {
    fun render(fmt: FormatSettings): List<String> {
        val out = ArrayList<String>()
        for (l in lines) when (l) {
            is Line.Text -> out.add(l.text)
            is Line.Val -> out.add(Formatter.format(l.value, fmt))
            is Line.Eq -> {
                val t = Formatter.format(l.value, fmt)
                out.add(if (t.startsWith("≈ ") && l.op == "=") "${l.left} ≈ ${t.drop(2)}" else "${l.left} ${l.op} $t")
            }
            is Line.IntervalSet -> out.addAll(renderIntervals(l, fmt))
        }
        return out
    }

    fun renderText(fmt: FormatSettings): String = render(fmt).joinToString("\n")

    private fun renderIntervals(l: Line.IntervalSet, fmt: FormatSettings): List<String> {
        fun f(v: Value) = Formatter.format(v, fmt)
        val v = l.variable
        val ineq = l.intervals.joinToString(" or ") { iv ->
            when {
                iv.lo != null && iv.hi != null && Ops.compare(iv.lo, iv.hi) == 0 && iv.loClosed && iv.hiClosed -> "$v = ${f(iv.lo)}"
                iv.lo == null && iv.hi != null -> "$v ${if (iv.hiClosed) "≤" else "<"} ${f(iv.hi)}"
                iv.lo != null && iv.hi == null -> "$v ${if (iv.loClosed) "≥" else ">"} ${f(iv.lo)}"
                iv.lo != null && iv.hi != null -> "${f(iv.lo)} ${if (iv.loClosed) "≤" else "<"} $v ${if (iv.hiClosed) "≤" else "<"} ${f(iv.hi)}"
                else -> "$v ∈ ℝ"
            }
        }
        val notation = l.intervals.joinToString(" ∪ ") { iv ->
            val lo = if (iv.lo == null) "(-∞" else (if (iv.loClosed) "[" else "(") + f(iv.lo)
            val hi = if (iv.hi == null) "∞)" else f(iv.hi) + (if (iv.hiClosed) "]" else ")")
            "$lo, $hi"
        }
        return listOf(ineq, notation)
    }
}
