package com.powermath.math

import com.powermath.graph.GraphFunction
import java.math.BigInteger

/** Extra commands: calculus, number theory and number bases. Called from Calculator.command. */
object Commands {
    val names = setOf("diff", "integrate", "isprime", "pfactor", "divisors", "totient", "nextprime", "bin", "oct", "hex", "tobase", "frombase")

    private fun text(vararg l: String, steps: List<String> = emptyList()) = Outcome(OutcomeKind.ALGEBRA, l.map { Line.Text(it) }, steps)

    private fun variableOf(c: Expr.Call, body: Expr, ws: Scope): Pair<String, Int> {
        val second = c.args.getOrNull(1)
        if (second is Expr.Var) return Pair(second.name, 2)
        val free = ExprUtil.freeVars(body).filter { ws.variable(it) == null && it != "ans" }
        if (free.size > 1) throw MathError(ErrorKind.SYNTAX, "${c.name}() contains ${free.joinToString(", ")}; say which one, e.g. ${c.name}(expression, x)")
        return Pair(free.firstOrNull() ?: "x", 1)
    }

    fun run(c: Expr.Call, ws: Workspace, s: CalcSettings, limits: EvalLimits): Outcome {
        fun ev() = Evaluator(ws, s.angle, limits)
        fun big(i: Int, what: String) = NumberTheory.toBig(ev().eval(c.args[i]), what)
        fun need(lo: Int, hi: Int = lo) { if (c.args.size !in lo..hi) throw MathError(ErrorKind.SYNTAX, "${c.name}() expects ${if (lo == hi) "$lo" else "$lo to $hi"} argument(s)") }
        val calc = Calculus(ws, s.angle)
        return when (c.name) {
            "diff" -> {
                need(1, 3)
                val body = c.args[0]
                val (v, next) = variableOf(c, body, ws)
                val d = calc.derivative(body, v)
                val shown = ExprPrinter.show(d)
                if (c.args.size > next) {
                    val at = ev().eval(c.args[next])
                    val value = Evaluator(ws, s.angle, limits, mapOf(v to at)).eval(d)
                    Outcome(OutcomeKind.VALUE, listOf(Line.Val(value)), listOf("d/d$v = $shown", "Evaluate at $v = ${Formatter.exact(at)}"), value = value)
                } else Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text("d/d$v = $shown")),
                    listOf("Apply the derivative rules (power, product, quotient and chain rule)"), graphExpr = shown)
            }
            "integrate" -> {
                need(1, 4)
                val body = c.args[0]
                val (v, next) = variableOf(c, body, ws)
                val F = calc.antiderivative(body, v)
                val hasBounds = c.args.size >= next + 2
                if (!hasBounds) {
                    if (c.args.size > next) throw MathError(ErrorKind.SYNTAX, "integrate(f, x, a, b) needs both bounds")
                    if (F == null) throw MathError(ErrorKind.UNSUPPORTED, "No closed form was found for this integral. Give bounds, as in integrate(f, x, 0, 1), for a numeric value.")
                    val shown = ExprPrinter.show(F)
                    Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text("∫ f d$v = $shown + C")), listOf("Integrate term by term and add the constant of integration"), graphExpr = shown)
                } else {
                    val a = ev().eval(c.args[next]); val b = ev().eval(c.args[next + 1])
                    if (F != null) {
                        val fb = Evaluator(ws, s.angle, limits, mapOf(v to b)).eval(F)
                        val fa = Evaluator(ws, s.angle, limits, mapOf(v to a)).eval(F)
                        val value = Ops.sub(fb, fa)
                        Outcome(OutcomeKind.VALUE, listOf(Line.Val(value)), listOf("Antiderivative F = ${ExprPrinter.show(F)}", "Value = F(${Formatter.exact(b)}) − F(${Formatter.exact(a)})"), value = value)
                    } else {
                        val f = GraphFunction.compileExpr(body, ws, s.angle, v)
                        val x = calc.numericIntegral({ f.eval(it) }, Ops.toDouble(a), Ops.toDouble(b))
                        val value = Value.D(x)
                        Outcome(OutcomeKind.VALUE, listOf(Line.Val(value)), listOf("No closed form is known, so the integral was computed numerically (adaptive Simpson)"), value = value)
                    }
                }
            }
            "isprime" -> {
                need(1); val n = big(0, "The number")
                if (NumberTheory.isPrime(n)) text("$n is prime") else {
                    if (n.signum() > 0 && n > BigInteger.ONE) text("$n is not prime", "$n = " + NumberTheory.factorText(NumberTheory.factorize(n)))
                    else text("$n is not prime")
                }
            }
            "pfactor" -> { need(1); val n = big(0, "The number"); text("$n = " + NumberTheory.factorText(NumberTheory.factorize(n)).ifEmpty { "1" }) }
            "divisors" -> {
                need(1); val n = big(0, "The number"); val d = NumberTheory.divisors(n)
                text("${d.size} divisors: " + d.joinToString(", "), "Sum of divisors: ${d.fold(BigInteger.ZERO) { a, b -> a + b }}")
            }
            "totient" -> { need(1); val n = big(0, "The number"); text("φ($n) = ${NumberTheory.totient(n)}") }
            "nextprime" -> { need(1); val n = big(0, "The number"); text("Next prime after $n: ${NumberTheory.nextPrime(n)}") }
            "bin", "oct", "hex" -> {
                need(1); val n = big(0, "The number"); val base = when (c.name) { "bin" -> 2; "oct" -> 8; else -> 16 }
                text("$n = ${NumberTheory.toBase(n, base)} (base $base)")
            }
            "tobase" -> {
                need(2); val n = big(0, "The number"); val b = big(1, "The base").toInt()
                text("$n = ${NumberTheory.toBase(n, b)} (base $b)")
            }
            "frombase" -> {
                need(2); val d = big(0, "The digits"); val b = big(1, "The base").toInt()
                text("$d in base $b = ${NumberTheory.fromBase(d, b)}")
            }
            else -> throw MathError(ErrorKind.UNDEFINED, "Unknown command '${c.name}'")
        }
    }
}
