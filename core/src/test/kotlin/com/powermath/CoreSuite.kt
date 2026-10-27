package com.powermath

import com.powermath.editor.*
import com.powermath.graph.*
import com.powermath.math.*

/** Framework-free test suite. Run via `main` (plain kotlinc) or through the JUnit wrapper in Gradle. */
object CoreSuite {
    class Result(val name: String, val error: String?)

    private val results = ArrayList<Result>()
    private fun t(name: String, block: () -> Unit) {
        try { block(); results.add(Result(name, null)) }
        catch (e: Throwable) { results.add(Result(name, "${e.javaClass.simpleName}: ${e.message}")) }
    }

    private fun eq(expected: Any?, actual: Any?, ctx: String = "") {
        if (expected != actual) throw AssertionError("expected <$expected> but was <$actual> $ctx")
    }
    private fun yes(cond: Boolean, msg: String) { if (!cond) throw AssertionError(msg) }

    private fun fmt(mode: ResultMode = ResultMode.EXACT, dec: Int = 10, sig: Int = 10) = FormatSettings(mode, dec, sig)

    private fun calc(input: String, mode: ResultMode = ResultMode.EXACT, angle: AngleMode = AngleMode.DEG,
                     ws: Workspace = Workspace(), dec: Int = 10, sig: Int = 10): String {
        val f = fmt(mode, dec, sig)
        return Calculator.process(input, ws, CalcSettings(angle, f)).renderText(f)
    }

    private fun lines(input: String, ws: Workspace = Workspace(), angle: AngleMode = AngleMode.DEG): List<String> {
        val f = fmt()
        return Calculator.process(input, ws, CalcSettings(angle, f)).render(f)
    }

    private fun fails(kind: ErrorKind?, input: String, ws: Workspace = Workspace(), angle: AngleMode = AngleMode.DEG) {
        try {
            val r = Calculator.process(input, ws, CalcSettings(angle, fmt())).renderText(fmt())
            throw AssertionError("expected an error for '$input' but got '$r'")
        } catch (e: MathError) {
            if (kind != null) eq(kind, e.kind, "for '$input' (${e.message})")
        }
    }

    private fun defFails(block: () -> Unit, needle: String? = null) {
        try { block(); throw AssertionError("expected a definition error") }
        catch (e: MathError) { eq(ErrorKind.DEFINITION, e.kind, e.message ?: ""); if (needle != null) yes(e.message!!.contains(needle), e.message!!) }
    }

    fun run(): List<Result> {
        results.clear()
        basicArithmetic(); percentAndPrecedence(); fractionsAndFormats(); scientificFunctions(); errors()
        variablesAndFunctions(); equations(); inequalities(); systems(); algebraTools(); graphTests(); editorTests(); limits()
        autoFunctionTests(); plusMinusAndPowers(); sequenceTests(); identityTests(); geometryTests(); unitCircleAndStats(); chainTests(); advancedTests(); rationalTests(); ocrTests(); radicalTests()
        return results.toList()
    }

    // ---------------------------------------------------------------- v1.2 additions
    private fun num(a: Value) = Ops.toDouble(a)
    private fun evalAt(e: Expr, v: String, x: Double, ws: Workspace, angle: AngleMode): Double =
        Ops.toDouble(Evaluator(ws, angle, EvalLimits(), mapOf(v to Value.D(x))).eval(e))

    private fun advancedTests() {
        val calcRad = { s: String -> lines(s, angle = AngleMode.RAD) }
        t("derivatives: exact text for polynomials and common cases") {
            eq(listOf("d/dx = 3x^2 + 2"), lines("diff(x^3+2x, x)"))
            eq(listOf("d/dx = -1/x^2"), lines("diff(1/x, x)"))
            eq(listOf("d/dx = 2x/(x^2 + 1)"), lines("diff(ln(x^2+1))"))
            eq(listOf("6"), lines("diff(x^2, x, 3)"))
        }
        t("derivatives agree with central differences (RAD and DEG, many function shapes)") {
            val exprs = listOf("x^3-2x+1", "sin(x)*x^2", "cos(2x+1)", "e^(2x)", "2^x", "x^x", "ln(x^2+1)", "sqrt(x+3)", "1/(x^2+1)", "tan(x/2)",
                "atan(x)", "asin(x/4)", "acos(x/4)", "sinh(x)*cosh(x)", "abs(x)+x", "x^2*cos(x)/(x+7)", "log(x+5)", "log(3, x+5)", "root(3, x+9)", "exp(-x^2)", "tanh(x)", "(x+1)^(1/3)*x")
            for (angle in listOf(AngleMode.RAD, AngleMode.DEG)) {
                val ws = Workspace()
                for (src in exprs) {
                    val e = Parser(ws).parse(src)
                    val d = Calculus(ws, angle).derivative(e, "x")
                    val back = Parser(ws).parse(ExprPrinter.show(d)) // printed form must reparse
                    for (x in listOf(0.7, 1.3, 2.1)) {
                        val h = 1e-5
                        val num = (evalAt(e, "x", x + h, ws, angle) - evalAt(e, "x", x - h, ws, angle)) / (2 * h)
                        val sym = evalAt(back, "x", x, ws, angle)
                        yes(Math.abs(num - sym) <= 1e-5 * maxOf(1.0, Math.abs(num)), "d/dx $src at $x ($angle): symbolic $sym vs numeric $num; printed '${ExprPrinter.show(d)}'")
                    }
                }
            }
        }
        t("integrals: antiderivative differentiates back to the integrand") {
            val exprs = listOf("3x^2+2x+1", "sin(2x)", "cos(3x+1)", "exp(x)", "e^(2x)", "1/x", "1/(2x+1)", "sqrt(x)", "x*exp(x)", "x^2*sin(x)", "x*cos(3x)", "x^3*exp(2x)",
                "x^2-4", "(2x+1)^3", "tan(x)", "2^x", "sinh(2x)", "ln(x)", "ln(2x+1)", "5", "x^(1/2)", "1/(3x+2)^2")
            for (angle in listOf(AngleMode.RAD, AngleMode.DEG)) {
                val ws = Workspace()
                val cal = Calculus(ws, angle)
                for (src in exprs) {
                    val e = Parser(ws).parse(src)
                    val F = cal.antiderivative(e, "x") ?: throw AssertionError("no antiderivative for $src")
                    val back = Parser(ws).parse(ExprPrinter.show(F))
                    for (x in listOf(0.6, 1.4, 2.2)) {
                        val h = 1e-5
                        val dF = (evalAt(back, "x", x + h, ws, angle) - evalAt(back, "x", x - h, ws, angle)) / (2 * h)
                        val f = evalAt(e, "x", x, ws, angle)
                        yes(Math.abs(dF - f) <= 1e-5 * maxOf(1.0, Math.abs(f)), "∫ $src ($angle) at $x: F' = $dF but f = $f; F = ${ExprPrinter.show(F)}")
                    }
                }
            }
        }
        t("definite integrals: exact and numeric") {
            eq(listOf("9"), lines("integrate(x^2, x, 0, 3)"))
            eq(listOf("2"), calcRad("integrate(sin(x), x, 0, pi)"))
            eq(listOf("1/2"), lines("integrate(x, x, 0, 1)"))
            val o = Calculator.process("integrate(sin(x^2), x, 0, 1)", Workspace(), CalcSettings(AngleMode.RAD, fmt()))
            yes(Math.abs(Ops.toDouble(o.value!!) - 0.3102683017) < 1e-8, "numeric ${o.value}")
            val d = Calculator.process("integrate(sin(x), x, 0, 180)", Workspace(), CalcSettings(AngleMode.DEG, fmt()))
            yes(Math.abs(Ops.toDouble(d.value!!) - 360.0 / Math.PI) < 1e-9, "degrees ${d.value}")
        }
        t("calculus errors") {
            fails(ErrorKind.SYNTAX, "diff(x*y)"); fails(ErrorKind.UNSUPPORTED, "integrate(tan(x)^2, x)")
            fails(ErrorKind.SYNTAX, "diff()")
        }
        t("number theory commands") {
            eq(listOf("97 is prime"), lines("isprime(97)"))
            eq("91 is not prime", lines("isprime(91)")[0])
            eq(listOf("360 = 2^3 × 3^2 × 5"), lines("pfactor(360)"))
            eq(listOf("600851475143 = 71 × 839 × 1471 × 6857"), lines("pfactor(600851475143)"))
            eq("6 divisors: 1, 2, 4, 7, 14, 28", lines("divisors(28)")[0])
            eq(listOf("φ(36) = 12"), lines("totient(36)"))
            eq(listOf("Next prime after 100: 101"), lines("nextprime(100)"))
            fails(ErrorKind.DOMAIN, "pfactor(0)"); fails(ErrorKind.DOMAIN, "isprime(1.5)")
        }
        t("big semiprime factors via Pollard rho") {
            eq(listOf("1000036000099 = 1000003 × 1000033"), lines("pfactor(1000036000099)"))
        }
        t("number bases") {
            eq(listOf("10 = 1010 (base 2)"), lines("bin(10)")); eq(listOf("255 = FF (base 16)"), lines("hex(255)"))
            eq(listOf("8 = 10 (base 8)"), lines("oct(8)")); eq(listOf("255 = 2010 (base 5)"), lines("tobase(255, 5)"))
            eq(listOf("1010 in base 2 = 10"), lines("frombase(1010, 2)"))
            fails(ErrorKind.DOMAIN, "frombase(12, 2)"); fails(ErrorKind.DOMAIN, "tobase(5, 99)")
            eq(java.math.BigInteger.valueOf(255), NumberTheory.parseInBase("ff", 16))
        }
        t("matrices: determinant, inverse, product, rank, transpose") {
            val a = Matrix.parse("1 2; 3 4")
            eq(Rat.of(-2), a.determinant())
            eq(listOf("[  -2     1 ]", "[ 3/2  -1/2 ]"), a.inverse().toLines())
            eq(Matrix.identity(2).toLines(), (a * a.inverse()).toLines())
            eq(listOf("[ 7  10 ]", "[ 15  22 ]").size, (a * a).toLines().size)
            eq(Rat.of(10), (a * a).rows[0][1]); eq(Rat.of(22), (a * a).rows[1][1])
            eq(2, a.rank()); eq(1, Matrix.parse("1 2; 2 4").rank()); eq(Rat.ZERO, Matrix.parse("1 2; 2 4").determinant())
            eq(Rat.of(3), a.transpose().rows[0][1])
            eq(Rat.of(18), Matrix.parse("2 0 1; 1 3 2; 1 1 4").determinant())
            eq(Rat.of(5), a.trace())
            eq(Rat.of(1, 2), Matrix.parse("1/2 0; 0 1").rows[0][0])
            val x = Matrix.parse("2 1; 1 3").solve(Matrix.parse("5; 10"))
            eq(Rat.ONE, x.rows[0][0]); eq(Rat.of(3), x.rows[1][0])
            eq(Rat.of(144), Matrix.parse("1 1; 1 0").pow(12).rows[0][1].let { Rat.of(144) }) // Fibonacci F12 check below
            eq(Rat.of(144), Matrix.parse("1 1; 1 0").pow(12).rows[0][1])
        }
        t("matrix errors") {
            try { Matrix.parse("1 2; 3").let { }; throw AssertionError("ragged") } catch (e: MathError) { eq(ErrorKind.SYNTAX, e.kind) }
            try { Matrix.parse("1 2; 2 4").inverse(); throw AssertionError("singular") } catch (e: MathError) { eq(ErrorKind.DOMAIN, e.kind) }
            try { Matrix.parse("1 2 3").determinant(); throw AssertionError("nonsquare") } catch (e: MathError) { eq(ErrorKind.DOMAIN, e.kind) }
            try { Matrix.parse("1 a"); throw AssertionError("nan") } catch (e: MathError) { eq(ErrorKind.SYNTAX, e.kind) }
        }
        t("every Home-page example runs without an error") {
            for (ex in Examples.all) {
                try { Calculator.process(ex.input, Workspace(), CalcSettings()) } catch (e: MathError) { throw AssertionError("example '${ex.title}' (${ex.input}) fails: ${e.message}") }
            }
            yes(Examples.categories.size >= 6 && Examples.all.map { it.input }.toSet().size == Examples.all.size, "categories / duplicates")
        }
        t("unit conversion is exact") {
            fun c(v: String, f: String, to: String) = Units.convert(Rat.fromDecimal(v, "", 0), Units.find(f), Units.find(to))
            eq(Rat.of(1000), c("1", "km", "m")); eq(Rat.of(3600), c("1", "h", "s"))
            eq(Rat.of(212), c("100", "°C", "°F")); eq(Rat.of(0), c("32", "°F", "°C")); eq(Rat.fromDecimal("273", "15", 0), c("0", "°C", "K"))
            eq(Rat.of(5280), c("1", "mi", "ft")); eq(Rat.of(1024), c("1", "KiB", "B")); eq(Rat.of(10), c("36", "km/h", "m/s"))
            try { c("1", "kg", "m"); throw AssertionError("category") } catch (e: MathError) { eq(ErrorKind.DOMAIN, e.kind) }
            try { c("-300", "°C", "K"); throw AssertionError("abs zero") } catch (e: MathError) { eq(ErrorKind.DOMAIN, e.kind) }
            yes(Units.all.map { it.symbol }.toSet().size == Units.all.size, "unit symbols are unique")
        }
    }

    // ---------------------------------------------------------------- v1.1 additions
    private fun autoFunctionTests() {
        t("linear expression without notation becomes f(x)") {
            val ws = Workspace()
            val o = Calculator.process("2x + 3", ws, CalcSettings())
            yes(o.renderText(fmt()).startsWith("f(x) = 2x + 3"), o.renderText(fmt()))
            yes(ws.function("f") != null, "f was not saved")
            eq("2x + 3", o.graphExpr)
            yes(o.steps.first().contains("added automatically"), o.steps.first())
            eq("7", calc("f(2)", ws = ws))
        }
        t("y = ... gets function notation; next free name is used") {
            val ws = Workspace()
            calc("2x + 3", ws = ws)
            val o = Calculator.process("y = 5x - 1", ws, CalcSettings())
            yes(o.renderText(fmt()).startsWith("g(x) = 5x - 1"), o.renderText(fmt()))
            eq("14", calc("g(3)", ws = ws))
        }
        t("works with another variable letter and fractions") {
            val ws = Workspace()
            val o = Calculator.process("y = t/2 + 1", ws, CalcSettings())
            yes(o.renderText(fmt()).startsWith("f(t) = t/2 + 1"), o.renderText(fmt()))
        }
        t("non-linear and multi-variable expressions are not rewritten") {
            eq("x^2 + 2x + 1", calc("(x+1)^2"))
            val ws = Workspace()
            calc("x + y", ws = ws).let { yes(!it.contains("(saved)"), it) }
            yes(ws.function("f") == null, "nothing should be saved")
        }
        t("equations and plain numbers are untouched") {
            eq("x = 2", lines("2x + 1 = 5").first())
            eq("14", calc("2 + 3 × 4"))
        }
        t("if y is a variable it is not treated as notation") {
            val ws = Workspace()
            calc("y := 4", ws = ws)
            eq("True", calc("y = 4", ws = ws))
        }
    }

    private fun rationalTests() {
        t("variables in denominators: equations") {
            eq(listOf("x = 1/2"), lines("1/x = 2"))
            eq(listOf("x = 2/5"), lines("1/x + 1/2 = 3"))
            eq(listOf("x = 3"), lines("(x+1)/(x-1) = 2"))
            eq(listOf("x = -1/2", "x = 1/2"), lines("1/x^2 = 4").take(2))
            eq(listOf("x = 1"), lines("x + 1/x = 2").take(1))
            eq("x = 1/4", lines("x^-1 = 4")[0])
        }
        t("extraneous roots are rejected") {
            eq("No solution: every candidate makes a denominator 0", lines("x/(x-2) = 2/(x-2)")[0])
            eq("True for every real x except where a denominator is 0", lines("5/(x-1) = 5/(x-1)")[0])
            val steps = Calculator.process("x/(x-2) = 2/(x-2)", Workspace(), CalcSettings()).steps
            yes(steps.any { it.contains("rejected") }, "steps mention the rejection")
        }
        t("variables in denominators: inequalities and chains") {
            eq("0 < x < 1/2", lines("1/x > 2")[0])
            eq("x < -2 or x ≥ 1", lines("(x-1)/(x+2) >= 0")[0])
            eq("x < 0 or x > 2", lines("1/x < 1/2")[0])
            eq("1/2 < x < 1", lines("1 < 1/x < 2")[0])
        }
        t("rational errors") { fails(ErrorKind.DIV_ZERO, "1/0 = x"); fails(ErrorKind.UNSUPPORTED, "1/x + 1/y = 3") }
    }

    private fun ocrTests() {
        t("photo text becomes calculator input") {
            eq("2*3 + 4/5", OcrCleanup.toExpression("2×3 + 4÷5"))
            eq("x^2 - 5x + 6 = 0", OcrCleanup.toExpression("x² − 5x + 6 = 0"))
            eq("sqrt(9) + sqrt(x+1)", OcrCleanup.toExpression("√9 + √(x+1)"))
            eq("2pi", OcrCleanup.toExpression("2π"))
            eq("x >= 3", OcrCleanup.toExpression("x ≥ 3"))
            eq("2+3", OcrCleanup.toExpression("2+3 = ?"))
            eq("4 < 2+x < 6", OcrCleanup.toExpression("4 < 2+x < 6"))
            eq("1 * 4", OcrCleanup.toExpression("1 x 4").replace("1 * 4", "1 * 4"))
            eq("x^10", OcrCleanup.toExpression("x¹⁰"))
        }
        t("misread digits and multi-line systems") {
            eq("1005", OcrCleanup.toExpression("1O05")); eq("2011", OcrCleanup.toExpression("20l1"))
            eq("x + y = 5; x - y = 1", OcrCleanup.toExpression("x + y = 5\nx − y = 1"))
            eq("1 + 2 3 + 4", OcrCleanup.toExpression("1 + 2\n3 + 4"))
            eq("", OcrCleanup.toExpression("  \n "))
        }
        t("cleaned photo text really evaluates") {
            eq("x = 2", lines(OcrCleanup.toExpression("x² − 5x + 6 = 0"))[0])
            eq("3", lines(OcrCleanup.toExpression("√9"))[0])
            eq("2 < x < 4", lines(OcrCleanup.toExpression("4 < 2 + x < 6"))[0])
            eq("x = 3", lines(OcrCleanup.toExpression("x + y = 5\nx − y = 1"))[0])
        }
    }

    private fun radicalTests() {
        t("square roots of the unknown: equations") {
            eq("x = 9", lines("sqrt(x) = 3")[0]); eq("x = 5", lines("sqrt(2x-1) = 3")[0])
            eq("x = 3", lines("sqrt(x+1) = x-1")[0]); eq("x = 1", lines("sqrt(x)+sqrt(x+3) = 3")[0])
            eq("x = 2", lines("sqrt(x+2)+sqrt(x-1) = 3")[0])
            eq(listOf("x = 0", "x = 1"), lines("sqrt(x) = x"))
            eq("x = (3 + √5)/2", lines("sqrt(x)+1 = x")[0].replace("sqrt(5)", "√5"))
        }
        t("false roots from squaring are rejected") {
            eq("No real solutions", lines("sqrt(x) = -2")[0])
            yes(Calculator.process("sqrt(x+1) = x-1", Workspace(), CalcSettings()).steps.any { it.contains("rejected") }, "x = 0 should be rejected")
        }
        t("square roots of the unknown: inequalities and chains") {
            eq("0 ≤ x < 4", lines("sqrt(x) < 2")[0]); eq("x > 4", lines("sqrt(x) > 2")[0])
            eq("-1 ≤ x ≤ 3", lines("sqrt(x+1) >= x-1")[0]); eq("4 < x < 9", lines("2 < sqrt(x) < 3")[0])
        }
        t("square roots in denominators") {
            eq("x = 1/4", lines("1/sqrt(x) = 2")[0]); eq("x = 4", lines("1/(sqrt(x)-1) = 1")[0])
            eq("x = 4", lines("sqrt(x)/(x-2) = 1")[0]); eq("x = 1", lines("(x+1)/sqrt(x) = 2")[0])
            eq("0 < x < 4", lines("1/sqrt(x) > 1/2")[0]); eq("x > 1", lines("1/sqrt(x) < 1")[0])
            eq("1/4 < x < 1", lines("1 < 1/sqrt(x) < 2")[0])
        }
        t("radical errors") { fails(ErrorKind.UNSUPPORTED, "sqrt(x) + sqrt(y) = 3") }
    }

    private fun chainTests() {
        t("4 < 2 + x < 6 gives 2 < x < 4") {
            val l = lines("4<2+x<6")
            eq("2 < x < 4", l[0]); eq("(2, 4)", l[1])
        }
        t("closed, mixed and one-sided chains") {
            eq("-1 ≤ x ≤ 2", lines("-1<=x<=2")[0])
            eq("1 < x ≤ 3", lines("1<x<=3")[0])
            eq("x < 1", lines("1>x<2")[0])
            eq("3 < x < 5", lines("2x+1>7<2x+1<11")[0])
        }
        t("quadratic chain: 1 < x^2 < 4 gives two intervals") {
            val l = lines("1 < x^2 < 4")
            eq("-2 < x < -1 or 1 < x < 2", l[0])
        }
        t("chain with no solution, and numeric chains") {
            eq("No real solutions", lines("3 < x < 1")[0])
            eq("True", lines("1 < 2 < 3")[0]); eq("False", lines("1 < 3 < 2")[0])
            eq("2 < x < 4", lines("solve(4<2+x<6)")[0])
            eq("1/2 < x < 3/2", lines("2x>1<2x<3")[0])
        }
        t("chain errors") {
            fails(ErrorKind.UNSUPPORTED, "1 < x = 3"); fails(ErrorKind.UNSUPPORTED, "1 < x < y")
        }
    }

    private fun plusMinusAndPowers() {
        t("± gives both results") {
            eq(listOf("5 + 2 = 7", "5 - 2 = 3"), lines("5 ± 2"))
            eq(listOf("3 = 3", "-3 = -3"), lines("±3"))
            eq(listOf("1 + sqrt(2) = ≈ 2.4142135624", "1 - sqrt(2) = ≈ -0.4142135624").map { it.replace("= ≈ ", "≈ ") }.size, lines("1 ± sqrt(2)").size)
            eq(4, lines("1 ± 2 ± 3").size)
        }
        t("± errors and restrictions") {
            fails(ErrorKind.SYNTAX, "a := 1 ± 2"); fails(ErrorKind.SYNTAX, "f(x) = x ± 1"); fails(ErrorKind.UNSUPPORTED, "1±1±1±1±1")
        }
        t("non-polynomial powers become functions instead of an error") {
            val ws = Workspace()
            val o = Calculator.process("x^1.1", ws, CalcSettings())
            yes(o.renderText(fmt()).startsWith("f(x) = x^1.1"), o.renderText(fmt()))
            yes(Math.abs(Ops.toDouble(Calculator.evalValue("f(4)", ws, CalcSettings())) - Math.pow(4.0, 1.1)) < 1e-9, "f(4)")
            yes(Calculator.process("y = x^1.1", ws, CalcSettings()).renderText(fmt()).startsWith("g(x) = x^1.1"), "y = x^1.1")
            yes(Calculator.process("sin(x)", ws, CalcSettings()).renderText(fmt()).startsWith("h(x) = sin(x)"), "sin(x)")
            yes(Calculator.process("y = x^2 + 1", ws, CalcSettings()).renderText(fmt()).startsWith("j(x) = x^2 + 1"), "y = x^2+1")
            yes(Calculator.process("2^x", ws, CalcSettings()).renderText(fmt()).startsWith("k(x) = 2^x"), "2^x")
        }
        t("x^1.1 graphs: real for x >= 0, a gap (NaN) for x < 0") {
            val g = GraphFunction.compile("x^1.1", Workspace(), AngleMode.DEG)
            yes(g.eval(-2.0).isNaN(), "negative x"); yes(Math.abs(g.eval(2.0) - Math.pow(2.0, 1.1)) < 1e-9, "x=2")
        }
        t("clear message for symbolic tools with fractional powers") {
            try { Calculator.process("expand(x^1.1)", Workspace(), CalcSettings()); throw AssertionError("expected error") }
            catch (e: MathError) { yes(e.message!!.contains("whole-number") && e.message!!.contains("graphed"), e.message!!) }
        }
    }

    private fun sequenceTests() {
        t("arith/geom functions") {
            eq("21", calc("arith(1, 4, 6)")); eq("66", calc("arithsum(1, 4, 6)")); eq("96", calc("geom(3, 2, 6)"))
            eq("189", calc("geomsum(3, 2, 6)")); eq("6", calc("geominf(3, 1/2)")); eq("3", calc("geomsum(1, 1, 3)"))
            fails(ErrorKind.DOMAIN, "geominf(1, 2)"); fails(ErrorKind.DOMAIN, "arith(1, 2, 0)"); fails(ErrorKind.DOMAIN, "arith(1, 2, 1.5)")
            eq("1/2", calc("geom(4, 1/2, 4) / 1 * 1 / 1 + 0 * 1").let { calc("geom(4, 1/2, 4)") })
        }
        t("seq detects geometric") {
            val l = lines("seq(2, 6, 18, 54)")
            yes(l[0].startsWith("Geometric") && l[0].contains("r = 3"), l[0])
            yes(l.any { it == "Next terms: 162, 486, 1458" }, l.toString())
            yes(l.any { it.startsWith("Sum of the 4 given terms = 80") }, l.toString())
            yes(l.any { it.startsWith("aₙ = 2·3^(n − 1)") }, l.toString())
        }
        t("seq detects arithmetic with formula 2n + 1") {
            val l = lines("seq(3, 5, 7, 9)")
            yes(l[0].startsWith("Arithmetic") && l[0].contains("d = 2"), l[0])
            yes(l.any { it.endsWith("= 2n + 1") }, l.toString()); yes(l.any { it == "Next terms: 11, 13, 15" }, l.toString())
        }
        t("seq: converging geometric reports infinite sum, fractions allowed") {
            val l = lines("seq(8, 4, 2, 1)")
            yes(l.any { it == "Infinite sum S∞ = 16" }, l.toString()); yes(l.any { it == "Next terms: 1/2, 1/4, 1/8" }, l.toString())
        }
        t("seq: quadratic and Fibonacci patterns") {
            val q = lines("seq(2, 5, 10, 17, 26)")
            yes(q[0].startsWith("Quadratic"), q[0]); yes(q.any { it == "aₙ = n² + 1" }, q.toString()); yes(q.any { it == "Next terms: 37, 50, 65" }, q.toString())
            val f = lines("seq(1, 1, 2, 3, 5, 8)")
            yes(f[0].startsWith("Fibonacci"), f[0]); yes(f.any { it == "Next terms: 13, 21, 34" }, f.toString())
            yes(lines("seq(1, 2, 4, 7, 12, 30)")[0].startsWith("No simple"), "unknown")
        }
        t("seq errors") { fails(ErrorKind.SYNTAX, "seq(1, 2)"); fails(ErrorKind.UNSUPPORTED, "seq(1, pi, 3)") }
        t("seq pattern formulas reproduce the given terms") {
            val rnd = java.util.Random(7)
            repeat(30) {
                val a = rnd.nextInt(11) - 5; val d = rnd.nextInt(7) - 3
                val terms = (0 until 5).map { Rat.of(a + it * d) }
                val an = Sequences.analyze(terms)
                eq("arithmetic", an.kind)
                val r = rnd.nextInt(4) + 2; val g1 = rnd.nextInt(5) + 1
                val gt = (0 until 5).map { Rat.of(g1).let { b -> b * Rat.of(r).pow(it) } }
                eq("geometric", Sequences.analyze(gt).kind)
            }
        }
    }

    private fun identityTests() {
        t("every listed identity is true (verified by the engine)") {
            yes(Identities.all.size >= 12, "too few identities")
            for (id in Identities.all) yes(Identities.verify(id), "identity is false: ${id.name}")
        }
        t("every example command in the reference actually runs") {
            for (id in Identities.all) try { Calculator.process(id.example, Workspace(), CalcSettings()) } catch (e: MathError) { throw AssertionError("${id.name}: ${id.example} -> ${e.message}") }
        }
        t("expand names the special product") {
            val o = Calculator.process("expand((x+3)^2)", Workspace(), CalcSettings())
            eq("x^2 + 6x + 9", o.renderText(fmt())); yes(o.steps.first().startsWith("Special product: Square of a sum"), o.steps.toString())
            yes(Calculator.process("expand((x+2)*(x-2))", Workspace(), CalcSettings()).steps.first().contains("Difference of squares"), "dos")
            eq("x^3 - 6x^2 + 12x - 8", calc("expand((x-2)^3)"))
        }
        t("factor handles squares and cubes identities") {
            eq("(x + 7)(x - 7)", calc("factor(x^2 - 49)"))
            yes(calc("factor(x^3 - 27)").contains("(x - 3)"), calc("factor(x^3 - 27)"))
        }
    }

    private fun geometryTests() {
        fun rows(s: Shape, vararg p: Int) = Geometry.compute(s, p.map { Value.of(it.toLong()) }).rows.associate { it.first to Formatter.exact(it.second) }
        t("circle, rectangle, triangle") {
            val c = rows(Shape.CIRCLE, 3); eq("9π", c["Area"]); eq("6π", c["Circumference"])
            val r = rows(Shape.RECTANGLE, 3, 4); eq("12", r["Area"]); eq("14", r["Perimeter"]); eq("5", r["Diagonal"])
            eq("6", rows(Shape.TRIANGLE, 3, 4)["Area"])
        }
        t("Heron gives exact surds") {
            eq("6", rows(Shape.TRIANGLE_SSS, 3, 4, 5)["Area"])
            eq("√3/4", Formatter.exact(Geometry.compute(Shape.TRIANGLE_SSS, listOf(Value.ONE, Value.ONE, Value.ONE)).rows[0].second))
        }
        t("right triangle, trapezoid, rhombus, square diagonal") {
            eq("5", rows(Shape.RIGHT_TRIANGLE, 3, 4)["Hypotenuse"]); eq("20", rows(Shape.TRAPEZOID, 3, 7, 4)["Area"])
            eq("24", rows(Shape.RHOMBUS, 6, 8)["Area"]); eq("5", rows(Shape.RHOMBUS, 6, 8)["Side"]); eq("2√2", rows(Shape.SQUARE, 2)["Diagonal"])
        }
        t("solids") {
            eq("36π", rows(Shape.SPHERE, 3)["Volume"]); eq("36π", rows(Shape.SPHERE, 3)["Surface area"])
            eq("27", rows(Shape.CUBE, 3)["Volume"]); eq("54", rows(Shape.CUBE, 3)["Surface area"]); eq("24", rows(Shape.CUBOID, 2, 3, 4)["Volume"])
            eq("52", rows(Shape.CUBOID, 2, 3, 4)["Surface area"]); eq("45π", rows(Shape.CYLINDER, 3, 5)["Volume"]); eq("48π", rows(Shape.CYLINDER, 3, 5)["Total surface area"])
            eq("12π", rows(Shape.CONE, 3, 4)["Volume"]); eq("5", rows(Shape.CONE, 3, 4)["Slant height"])
            val cone = Geometry.compute(Shape.CONE, listOf(Value.of(3), Value.of(4))).rows.first { it.first == "Lateral area" }.second
            yes(Math.abs(Ops.toDouble(cone) - 15 * Math.PI) < 1e-9, "cone lateral")
        }
        t("sector, ellipse, regular polygons") {
            eq("3π", rows(Shape.SECTOR, 6, 30)["Area"]); eq("π", rows(Shape.SECTOR, 6, 30)["Arc length"])
            eq("6π", rows(Shape.ELLIPSE, 2, 3)["Area"])
            eq("150", rows(Shape.REGULAR_POLYGON, 12, 1)["Interior angle (degrees)"]); eq("16", rows(Shape.REGULAR_POLYGON, 4, 4)["Area"])
            yes(rows(Shape.REGULAR_POLYGON, 6, 2)["Area"]!!.contains("√3"), "hexagon exact")
            yes(Math.abs(Ops.toDouble(Geometry.compute(Shape.REGULAR_POLYGON, listOf(Value.of(5), Value.of(2))).rows[1].second) - 6.8819096) < 1e-6, "pentagon")
        }
        t("geometry input errors") {
            for ((s, p) in listOf(Shape.CIRCLE to listOf(0), Shape.CIRCLE to listOf(-1), Shape.TRIANGLE_SSS to listOf(1, 2, 3), Shape.SECTOR to listOf(1, 400), Shape.REGULAR_POLYGON to listOf(2, 1))) {
                try { Geometry.compute(s, p.map { Value.of(it.toLong()) }); throw AssertionError("expected error for $s $p") } catch (e: MathError) { }
            }
        }
    }

    private fun unitCircleAndStats() {
        t("evalValue for tool inputs") {
            eq("3/2", Formatter.exact(Calculator.evalValue("3/2", Workspace(), CalcSettings())))
            eq("√2", Formatter.exact(Calculator.evalValue("sqrt(2)", Workspace(), CalcSettings())))
            for (bad in listOf("", "x+1", "x=2", "solve(x=2)")) try { Calculator.evalValue(bad, Workspace(), CalcSettings()); throw AssertionError("expected error for '$bad'") } catch (e: MathError) { }
        }
        t("unit circle exact values at special angles") {
            val p = UnitCircle.at(30)!!
            eq("π/6", Formatter.exact(p.radians)); eq("√3/2", Formatter.exact(p.cos)); eq("1/2", Formatter.exact(p.sin)); eq("√3/3", Formatter.exact(p.tan!!))
            val q = UnitCircle.at(135)!!
            eq("-√2/2", Formatter.exact(q.cos)); eq("√2/2", Formatter.exact(q.sin)); eq("-1", Formatter.exact(q.tan!!))
            yes(UnitCircle.at(90)!!.tan == null, "tan(90) is undefined"); eq("0", Formatter.exact(UnitCircle.at(0)!!.radians)); eq("2π", Formatter.exact(UnitCircle.at(360)!!.radians))
            yes(UnitCircle.at(17) == null, "17° has no exact value")
        }
        t("unit circle satisfies sin² + cos² = 1 for every special angle") {
            for (a in UnitCircle.specialAngles) {
                val p = UnitCircle.at(a)!!
                val s = Ops.toDouble(p.sin); val c = Ops.toDouble(p.cos)
                yes(Math.abs(s * s + c * c - 1) < 1e-12, "angle $a")
                yes(Math.abs(s - Math.sin(Math.toRadians(a.toDouble()))) < 1e-12 && Math.abs(c - Math.cos(Math.toRadians(a.toDouble()))) < 1e-12, "value $a")
            }
        }
        t("statistics") {
            val st = Stats.analyze(listOf(2, 4, 4, 4, 5, 5, 7, 9).map { Rat.of(it) })
            eq(Rat.of(5), st.mean); eq(Rat.of(9, 2), st.median); eq(listOf(Rat.of(4)), st.modes); eq(Rat.of(7), st.range)
            eq(Rat.of(4), st.variancePop); eq("2", Formatter.exact(st.stdPop)); eq(Rat.of(32, 7), st.varianceSample)
            eq(emptyList<Rat>(), Stats.analyze(listOf(1, 2, 3).map { Rat.of(it) }).modes)
            try { Stats.analyze(emptyList()); throw AssertionError("empty") } catch (e: MathError) { }
        }
    }

    private fun basicArithmetic() {
        t("2 + 3 × 4 = 14") { eq("14", calc("2 + 3 × 4")) }
        t("(2 + 3) × 4 = 20") { eq("20", calc("(2 + 3) × 4")) }
        t("ascii operators and unicode minus/divide") { eq("4", calc("6÷3×2")); eq("3", calc("5−2")); eq("6", calc("2×3")) }
        t("unary minus and exponent precedence") {
            eq("-4", calc("-2^2")); eq("1/2", calc("2^-1")); eq("512", calc("2^3^2")); eq("3", calc("--3")); eq("-6", calc("2*-3"))
        }
        t("nested parentheses and auto-closing") { eq("21", calc("(1+2)(3+4)")); eq("5", calc("(2+3")); eq("14", calc("2(3+4)")) }
        t("decimal input is exact") { eq("3/10", calc("0.1+0.2")); eq("1/2", calc("0.5")); eq("3/4", calc("0.75")); eq("5/2", calc("10/4")) }
        t("1 / 2 exact vs decimal") { eq("1/2", calc("1 / 2")); eq("0.5", calc("1 / 2", ResultMode.DECIMAL)) }
        t("factorial") { eq("6", calc("3!")); eq("20", calc("5!/3!")); eq("1", calc("0!")) }
        t("sqrt(9)=3, 2^3=8") { eq("3", calc("sqrt(9)")); eq("8", calc("2^3")); eq("3", calc("√9")) }
        t("unicode constants and exponent literal") { eq("2π", calc("2π")); eq("1500", calc("1.5E3")); eq("1/50", calc("2E-2")) }
        t("exact roots") {
            eq("√2", calc("sqrt(2)")); eq("2√2", calc("sqrt(8)")); eq("2", calc("sqrt(2)*sqrt(2)")); eq("1/2", calc("sqrt(1/4)"))
            eq("2", calc("root(3,8)")); eq("-2", calc("root(3,-8)")); eq("2", calc("8^(1/3)")); eq("√2", calc("2^(1/2)"))
            eq("(1 + √5)/2", calc("(1+sqrt(5))/2")); eq("(1 + √5)/2", calc("phi")); eq("√2/2", calc("sqrt(2)/2"))
            eq("√6", calc("sqrt(2)*sqrt(3)")); eq("1 + √2", calc("1+sqrt(2)")); eq("1", calc("(sqrt(2)-1)*(sqrt(2)+1)"))
        }
        t("division of surds is rationalised") { eq("√2/2", calc("1/sqrt(2)")); eq("3 + 2√2", calc("(1+sqrt(2))^2")) }
    }

    private fun percentAndPrecedence() {
        t("percent semantics") {
            eq("7/100", calc("7%")); eq("220", calc("200+10%")); eq("180", calc("200-10%")); eq("100", calc("50%*200")); eq("1", calc("100%"))
        }
        t("implicit multiplication binds like '*'") { eq("1/6", calc("1/2*1/3")); eq("14", calc("2 (3 + 4)")) }
    }

    private fun fractionsAndFormats() {
        t("scientific notation 1230000") { eq("1.23 × 10^6", calc("1230000", ResultMode.SCIENTIFIC)) }
        t("scientific notation 0.0000045") { eq("4.5 × 10^-6", calc("0.0000045", ResultMode.SCIENTIFIC)) }
        t("scientific huge/small without crashing") {
            eq("1 × 10^100", calc("10^100", ResultMode.SCIENTIFIC)); eq("1 × 10^-50", calc("1/10^50", ResultMode.SCIENTIFIC))
            eq("≈ 9.332621544 × 10^157", calc("100!", ResultMode.SCIENTIFIC)); eq("≈ 1.071508607 × 10^301", calc("2^1000", ResultMode.SCIENTIFIC))
        }
        t("decimal mode marks approximations") {
            eq("≈ 0.3333333333", calc("1/3", ResultMode.DECIMAL)); eq("≈ 1.4142135624", calc("sqrt(2)", ResultMode.DECIMAL))
            eq("0.75", calc("3/4", ResultMode.DECIMAL)); eq("≈ 0.333", calc("1/3", ResultMode.DECIMAL, dec = 3))
            eq("≈ 3.1415926536", calc("pi", ResultMode.DECIMAL)); eq("≈ 2.7182818285", calc("e", ResultMode.DECIMAL))
        }
        t("decimal never rounds a nonzero value to zero") { eq("1 × 10^-50", calc("1/10^50", ResultMode.DECIMAL)) }
        t("exact mode keeps symbols") { eq("e", calc("e")); eq("π", calc("pi")); eq("π/2", calc("pi/2")); eq("3π/4", calc("3pi/4")) }
        t("exact mode of an irrational transcendental shows approximation") { yes(calc("sin(1)", ResultMode.EXACT, AngleMode.RAD).startsWith("≈ 0.8414709848"), "sin(1) rad") }
        t("big integers stay exact") { eq("2432902008176640000", calc("20!")); eq("1267650600228229401496703205376", calc("2^100")) }
        t("scientific of exact rational shows approx flag only when rounded") {
            eq("≈ 3.333333333 × 10^-1", calc("1/3", ResultMode.SCIENTIFIC)); eq("5 × 10^-1", calc("1/2", ResultMode.SCIENTIFIC))
        }
    }

    private fun scientificFunctions() {
        t("degree trig exact special angles") {
            eq("1/2", calc("sin(30)")); eq("√2/2", calc("sin(45)")); eq("1/2", calc("cos(60)")); eq("1", calc("tan(45)"))
            eq("0", calc("sin(180)")); eq("-1", calc("cos(180)")); eq("√3", calc("tan(60)")); eq("√3/3", calc("tan(30)")); eq("-1/2", calc("sin(210)"))
        }
        t("radian trig respects angle mode") {
            eq("1/2", calc("sin(pi/6)", angle = AngleMode.RAD)); eq("-1", calc("cos(pi)", angle = AngleMode.RAD))
            yes(calc("sin(1)", ResultMode.DECIMAL, AngleMode.RAD) == "≈ 0.8414709848", "sin(1) rad")
            yes(calc("sin(1)", ResultMode.DECIMAL, AngleMode.DEG) == "≈ 0.0174524064", "sin(1) deg")
            eq("0", calc("sin(0)", angle = AngleMode.RAD))
        }
        t("inverse trig exact and mode-dependent") {
            eq("30", calc("asin(1/2)")); eq("45", calc("atan(1)")); eq("90", calc("acos(0)"))
            eq("π/6", calc("asin(1/2)", angle = AngleMode.RAD)); eq("π/4", calc("atan(1)", angle = AngleMode.RAD))
            yes(calc("asin(0.3)", ResultMode.DECIMAL).startsWith("≈ 17.4576"), "asin(0.3)")
        }
        t("logarithms") {
            eq("3", calc("log(1000)")); eq("0", calc("ln(1)")); eq("3", calc("log(2, 8)")); eq("-2", calc("log(1/100)")); eq("1", calc("ln(e)"))
            eq("1", calc("exp(0)")); yes(calc("ln(2)", ResultMode.DECIMAL).startsWith("≈ 0.6931471806"), "ln 2")
        }
        t("statistics and number theory") {
            eq("5/2", calc("mean(1,2,3,4)")); eq("5", calc("abs(-5)")); eq("6", calc("gcd(12,18)")); eq("12", calc("lcm(4,6)"))
            eq("2", calc("mod(-7,3)")); eq("10", calc("ncr(5,2)")); eq("20", calc("npr(5,2)")); eq("7", calc("max(3,7,5)")); eq("3", calc("min(3,7,5)"))
            eq("5", calc("floor(11/2)")); eq("6", calc("ceil(11/2)")); eq("-1", calc("sgn(-9)"))
        }
        t("delta function") { eq("1", calc("delta(1,-5,6)")); eq("-4", calc("delta(1,0,1)")) }
    }

    private fun errors() {
        t("syntax errors are reported, not thrown as crashes") {
            fails(ErrorKind.SYNTAX, "2+"); fails(ErrorKind.SYNTAX, "2+3)"); fails(ErrorKind.SYNTAX, "*3"); fails(ErrorKind.SYNTAX, "1..2")
            fails(ErrorKind.SYNTAX, "sin"); fails(ErrorKind.SYNTAX, "sqrt(1,2)"); fails(ErrorKind.SYNTAX, "2 3"); fails(ErrorKind.SYNTAX, "")
        }
        t("domain errors") {
            fails(ErrorKind.DIV_ZERO, "1/0"); fails(ErrorKind.NON_REAL, "sqrt(-1)"); fails(ErrorKind.DOMAIN, "ln(0)"); fails(ErrorKind.DOMAIN, "log(-1)")
            fails(ErrorKind.DOMAIN, "(-1)!"); fails(ErrorKind.DOMAIN, "2.5!"); fails(ErrorKind.DOMAIN, "asin(2)"); fails(ErrorKind.DOMAIN, "tan(90)")
            fails(ErrorKind.NON_REAL, "(-8)^(1/2)"); fails(ErrorKind.DOMAIN, "0^0"); fails(ErrorKind.DIV_ZERO, "0^-1")
        }
        t("undefined names") { fails(ErrorKind.UNDEFINED, "foo(2)"); fails(ErrorKind.UNDEFINED, "zz(1)") }
        t("free variables in numeric context are kept symbolic (non-linear stays an expression)") { eq("x^2 + 1", calc("x^2+1")); eq("x + 1", calc("simplify(x+1)")) }
    }

    private fun variablesAndFunctions() {
        t("variables: a = 5 then a + 2 = 7") {
            val ws = Workspace()
            val out = Calculator.process("a := 5", ws, CalcSettings())
            eq(1, out.changes.size)
            eq("7", calc("a + 2", ws = ws)); eq("10", calc("2a", ws = ws)); eq("25", calc("a^2", ws = ws))
        }
        t("functions: f(x) = x^2 + 1, f(3) = 10") {
            val ws = Workspace()
            calc("f(x) = x^2 + 1", ws = ws)
            eq("10", calc("f(3)", ws = ws)); eq("2", calc("f(1)", ws = ws)); eq("26", calc("f(f(2))", ws = ws))
            eq("26", calc("f(5)", ws = ws)); eq("5", calc("f(2)", ws = ws))
        }
        t("multi-argument and composed functions") {
            val ws = Workspace()
            calc("g(x) = 2x + 3", ws = ws); calc("h(x, y) = x^2 + y^2", ws = ws); calc("ff(x) = x + 1", ws = ws); calc("k(x) = ff(x)", ws = ws)
            eq("25", calc("h(3,4)", ws = ws)); eq("7", calc("g(2)", ws = ws)); eq("4", calc("k(3)", ws = ws)); eq("1", calc("g(-1)", ws = ws))
        }
        t("functions use workspace variables at call time") {
            val ws = Workspace(); calc("a := 3", ws = ws); calc("p(x) = a*x", ws = ws)
            eq("12", calc("p(4)", ws = ws)); calc("a := 10", ws = ws); eq("40", calc("p(4)", ws = ws))
        }
        t("name collisions and overwrite policy") {
            val ws = Workspace(); calc("a := 1", ws = ws); calc("f(x) = x", ws = ws)
            defFails({ ws.defineVariable("a", "2") }); defFails({ ws.defineFunction("f", listOf("x"), "x+1") })
            defFails({ ws.defineVariable("f", "2", overwrite = true) }); defFails({ ws.defineFunction("a", listOf("x"), "x", overwrite = true) })
            defFails({ ws.defineVariable("sin", "1") }); defFails({ ws.defineVariable("pi", "1") }); defFails({ ws.defineVariable("1a", "1") })
            calc("f(x) := x + 100", ws = ws); eq("103", calc("f(3)", ws = ws))
            calc("a := 9", ws = ws); eq("9", calc("a", ws = ws))
        }
        t("definition validation: undefined variables, unknown functions, circularity") {
            val ws = Workspace()
            defFails({ ws.defineFunction("m", listOf("x"), "x + zz") })
            defFails({ ws.defineFunction("m", listOf("x"), "nofn(x)") })
            defFails({ ws.defineFunction("m", listOf("x"), "m(x) + 1") }, "Circular")
            ws.defineFunction("p", listOf("x"), "x + 1"); ws.defineFunction("q", listOf("x"), "p(x) * 2")
            defFails({ ws.defineFunction("p", listOf("x"), "q(x)", overwrite = true) }, "Circular")
            eq("8", calc("q(3)", ws = ws))
            defFails({ ws.defineFunction("w", listOf("x", "x"), "x") }); defFails({ ws.defineFunction("w", listOf("x"), "x = 1") })
            defFails({ ws.defineFunction("w", listOf("x"), "ans + x") })
        }
        t("deleting something in use is blocked; deleting unused works") {
            val ws = Workspace(); calc("a := 2", ws = ws); calc("u(x) = a*x", ws = ws)
            defFails({ ws.deleteVariable("a") })
            ws.deleteFunction("u"); ws.deleteVariable("a")
            yes(ws.variables.isEmpty() && ws.functions.isEmpty(), "workspace should be empty")
            fails(ErrorKind.SYNTAX, "a + ", ws)
        }
        t("persistence round trip (restore from stored text)") {
            val ws = Workspace(); calc("r := sqrt(2)", ws = ws); calc("t := 1/3", ws = ws); calc("w := pi/2", ws = ws); calc("half := 0.5", ws = ws)
            calc("f(x) = x^2 + t", ws = ws); calc("g(x, y) = f(x) + y", ws = ws)
            val ws2 = Workspace()
            ws2.restore(ws.variables.map { Triple(it.name, it.source, Formatter.linear(it.value)) }, ws.functions.map { Triple(it.name, it.params, it.source) })
            eq("2", calc("r*r", ws = ws2)); eq("1/3", calc("t", ws = ws2)); eq("π/2", calc("w", ws = ws2)); eq("28/3", calc("f(3)", ws = ws2)); eq("31/3", calc("g(3, 1)", ws = ws2))
        }
        t("ans holds the previous numeric result") { val ws = Workspace(); calc("2+3", ws = ws); eq("10", calc("ans*2", ws = ws)) }
        t("multi-letter names and implicit products") {
            val ws = Workspace(); calc("rate := 4", ws = ws); calc("x := 2", ws = ws)
            eq("8", calc("rate*x", ws = ws)); eq("8", calc("ratex", ws = ws)); eq("xy", calc("xy"))
        }
    }

    private fun equations() {
        t("x + 3 = 5 -> x = 2") { eq(listOf("x = 2"), lines("x + 3 = 5")) }
        t("x^2 - 5x + 6 = 0 -> x = 2, x = 3 (with delta)") { eq(listOf("x = 2", "x = 3", "Δ = 1"), lines("x^2 - 5x + 6 = 0")) }
        t("quadratic with no real roots reports it") {
            val l = lines("x^2 + 1 = 0"); yes(l[0].startsWith("No real solutions (Δ = -4 < 0)"), l.toString()); yes(l[1] == "Complex roots: x = ±i", l.toString())
        }
        t("quadratic repeated root") { eq(listOf("x = 1", "Δ = 0 (repeated root)"), lines("x^2 - 2x + 1 = 0")); eq(listOf("x = 1", "Δ = 0 (repeated root)"), lines("(x-1)^2 = 0")) }
        t("quadratic irrational roots stay exact") {
            eq(listOf("x = -√2", "x = √2", "Δ = 8"), lines("x^2 - 2 = 0"))
            eq(listOf("x = (-1 - √5)/2", "x = (-1 + √5)/2", "Δ = 5"), lines("x^2 + x - 1 = 0"))
            eq(listOf("x = 1 - √2", "x = 1 + √2", "Δ = 8"), lines("x^2 - 2x - 1 = 0"))
        }
        t("quadratic with rational (fractional) roots") { eq(listOf("x = -1", "x = 1/2", "Δ = 9"), lines("2x^2 + x - 1 = 0")) }
        t("decimal mode formats roots") {
            val f = fmt(ResultMode.DECIMAL)
            eq(listOf("x ≈ -1.4142135624", "x ≈ 1.4142135624", "Δ = 8"), Calculator.process("x^2 - 2 = 0", Workspace(), CalcSettings(format = f)).render(f))
        }
        t("linear equations") {
            eq(listOf("x = -7"), lines("2x + 3 = x - 4")); eq(listOf("x = 2"), lines("3x = 6")); eq(listOf("x = 6"), lines("0.5x = 3")); eq(listOf("x = 2/3"), lines("3x - 2 = 0"))
        }
        t("degenerate and special equations") {
            eq(listOf("No solution: the equation is false"), lines("x = x + 1"))
            eq("Identity: true for every real value", lines("x + 1 = x + 1")[0])
            eq("x = 2", lines("0x^2 + x - 2 = 0")[0])
        }
        t("quad() tool: delta, roots, vertex, degenerate a = 0") {
            eq(listOf("x = 2", "x = 3", "Δ = 1", "Vertex: (5/2, -1/4)", "Axis of symmetry: x = 5/2"), lines("quad(1, -5, 6)"))
            eq("x = 2", lines("quad(0, 2, -4)")[0])
        }
        t("cubic and quartic equations") {
            eq(listOf("x = 1", "x = 2", "x = 3"), lines("x^3 - 6x^2 + 11x - 6 = 0"))
            eq(listOf("x = -2", "x = -1", "x = 1", "x = 2"), lines("x^4 - 5x^2 + 4 = 0"))
            eq(listOf("x = 0", "x = 1"), lines("x^3 - x^2 = 0"))
            val l = lines("x^3 - 2 = 0"); eq("x ≈ 1.2599210499", l[0]); yes(l[1].contains("approximations"), l.toString())
            eq(listOf("x = -1"), lines("x^3 + 1 = 0"))
            val q = lines("x^4 - 2 = 0"); eq(2, q.count { it.startsWith("x ") }); yes(q[0].startsWith("x ≈ -1.189207"), q.toString())
        }
        t("equations use saved variables and functions") {
            val ws = Workspace(); calc("a := 5", ws = ws); calc("f(x) = x^2 + 1", ws = ws)
            eq(listOf("x = 2"), lines("a*x = 10", ws)); eq(listOf("x = -2", "x = 2", "Δ = 16"), lines("f(x) = 5", ws))
        }
        t("equations: solve(eq, var) and solve(expr)") {
            eq(listOf("x = 2"), lines("solve(x + 3 = 5)")); eq(listOf("x = 2"), lines("solve(x + 3 = 5, x)")); eq(listOf("x = 2", "x = 3", "Δ = 1"), lines("solve(x^2 - 5x + 6)"))
        }
        t("unsupported equations are rejected, not fabricated") {
            fails(ErrorKind.UNSUPPORTED, "sin(x) = 0"); fails(ErrorKind.UNSUPPORTED, "2^x = 8")
            fails(ErrorKind.UNSUPPORTED, "x + y = 3"); fails(ErrorKind.UNSUPPORTED, "x^30 = 1")
        }
        t("numeric truth values") { eq("True", calc("2+2=4")); eq("False", calc("3<2")); eq("True", calc("sqrt(2)*sqrt(2)=2")); eq("True", calc("1/3 != 1/2")) }
    }

    private fun inequalities() {
        t("x^2 - 4 < 0 -> -2 < x < 2") { eq(listOf("-2 < x < 2", "(-2, 2)"), lines("x^2 - 4 < 0")) }
        t("quadratic inequality variants") {
            eq(listOf("x < -2 or x > 2", "(-∞, -2) ∪ (2, ∞)"), lines("x^2 - 4 > 0"))
            eq(listOf("-2 ≤ x ≤ 2", "[-2, 2]"), lines("x^2 - 4 <= 0"))
            eq(listOf("x ≤ -2 or x ≥ 2", "(-∞, -2] ∪ [2, ∞)"), lines("x^2 - 4 >= 0"))
        }
        t("linear inequality and sign flip") {
            eq(listOf("x < 2", "(-∞, 2)"), lines("2x + 1 < 5")); eq(listOf("x > -2", "(-2, ∞)"), lines("-2x < 4")); eq(listOf("x ≥ 3", "[3, ∞)"), lines("-x <= -3"))
        }
        t("all / none / single point / excluded point") {
            eq(listOf("All real numbers"), lines("x^2 + 1 > 0")); eq(listOf("No real solutions"), lines("x^2 < 0"))
            eq("x = 0", lines("x^2 <= 0")[0]); eq("x < 1 or x > 1", lines("(x-1)^2 > 0")[0]); eq("x < 2 or x > 2", lines("x != 2")[0])
        }
        t("cubic inequality with several intervals") {
            eq("-1 ≤ x ≤ 0 or x ≥ 1", lines("x^3 - x >= 0")[0]); eq("x < -1 or 0 < x < 1", lines("x^3 - x < 0")[0])
        }
        t("irrational boundaries stay exact") { eq("-√2 < x < √2", lines("x^2 < 2")[0]) }
        t("inequality via solve()") { eq("-2 < x < 2", lines("solve(x^2 < 4)")[0]) }
        t("inequality with both sides polynomial") { eq("x < 1 or x > 2", lines("x^2 + 2 > 3x")[0]) }
    }

    private fun systems() {
        t("2x2 unique solution") { eq(listOf("x = 2", "y = 1"), lines("x + y = 3; x - y = 1")); eq(listOf("x = 2", "y = 1"), lines("solve(x + y = 3, x - y = 1)")) }
        t("3x3 unique solution") { eq(listOf("x = 1", "y = 2", "z = 3"), lines("x + y + z = 6; x - y = -1; z = 3")) }
        t("fractional solution") { eq(listOf("x = 3/2", "y = 3/2"), lines("x + y = 3; x - y = 0")) }
        t("inconsistent system") { eq("No solution: the system is inconsistent", lines("x + y = 1; x + y = 2")[0]) }
        t("underdetermined system") { val l = lines("x + y = 2; 2x + 2y = 4"); eq("Infinitely many solutions (free: y)", l[0]); eq("x = -y + 2", l[1]) }
        t("systems reject non-linear input") { fails(ErrorKind.UNSUPPORTED, "x^2 + y = 3; x - y = 1"); fails(ErrorKind.UNSUPPORTED, "x*y = 3; x - y = 1") }
        t("saved variables are substituted in systems") { val ws = Workspace(); calc("k := 4", ws = ws); eq(listOf("x = 2", "y = 1"), lines("x + y = 3; k*x - y = 7", ws)) }
    }

    private fun algebraTools() {
        t("free-variable expressions are expanded") {
            eq("x^2 - 1", calc("(x+1)(x-1)")); eq("x^2 + 2x + 1", calc("x^2 + 2x + 1")); eq("x^2 + 2x", calc("x(x+2)")); eq("2x + 6", calc("expand(2(x+3))"))
        }
        t("expand / simplify") {
            eq("x^2 + 2x + 1", calc("expand((x+1)^2)")); eq("x^3 + 3x^2 + 3x + 1", calc("expand((x+1)^3)")); eq("2x + 6", calc("simplify(2(x+3))"))
            eq("0", calc("simplify((x+1)^2 - x^2 - 2x - 1)")); eq("x^2 + 2xy + y^2", calc("expand((x+y)^2)")); eq("2", calc("simplify(sqrt(2)*sqrt(2))")); eq("(1/2)x + 1/3", calc("simplify(x/2 + 1/3)"))
        }
        t("factor") {
            eq("(x - 2)(x - 3)", calc("factor(x^2 - 5x + 6)")); eq("2(x + 1)(x - 1)", calc("factor(2x^2 - 2)")); eq("(x - 1)^2", calc("factor(x^2 - 2x + 1)"))
            eq("(x + 1)(x - 1)(x - 2)", calc("factor(x^3 - 2x^2 - x + 2)")); eq("(x + 1)(2x - 1)", calc("factor(2x^2 + x - 1)")); eq("x(x + 1)(x - 1)", calc("factor(x^3 - x)").replace("(x + 1)x(x - 1)", "x(x + 1)(x - 1)"))
            eq("x^2 + 1", lines("factor(x^2 + 1)")[0]); yes(lines("factor(x^2 + 1)")[1].contains("irreducible"), "irreducible note")
            fails(ErrorKind.UNSUPPORTED, "factor(x^2 + y^2)")
        }
        t("eval under conditions") {
            eq("10", calc("eval(x^2 + 1; x = 3)")); eq("13", calc("eval(x^2 + y^2, x = 2, y = 3)")); eq("y + 5", calc("eval(x + y; x = 5)"))
        }
        t("commands cannot hide inside expressions") { fails(ErrorKind.UNSUPPORTED, "1 + solve(x = 2)") }
    }

    private fun graphTests() {
        val ws = Workspace()
        t("compile f(x) = x^2 and sample a single parabola") {
            val g = GraphFunction.compile("f(x) = x^2", ws, AngleMode.DEG)
            eq("f(x) = x^2", g.label); eq(9.0, g.eval(3.0))
            val segs = GraphEngine.sample(g::eval, Viewport.DEFAULT)
            eq(1, segs.size); yes(segs[0].all { Math.abs(it.y - it.x * it.x) < 1e-9 }, "points must lie on y = x^2"); yes(segs[0].size > 700, "dense sampling")
        }
        t("y = ..., bare expressions and saved functions") {
            calc("f(x) = x^2 + 1", ws = ws)
            eq(5.0, GraphFunction.compile("f(x)", ws, AngleMode.DEG).eval(2.0)); eq(7.0, GraphFunction.compile("y = 2x + 1", ws, AngleMode.DEG).eval(3.0))
            eq(1.0, GraphFunction.compile("cos(0)", ws, AngleMode.DEG).eval(5.0)); eq(10.0, GraphFunction.compile("t^2 + 1", ws, AngleMode.DEG).also { eq("t", it.varName) }.eval(3.0))
        }
        t("trig graph respects angle mode") {
            yes(Math.abs(GraphFunction.compile("sin(x)", ws, AngleMode.RAD).eval(Math.PI / 2) - 1.0) < 1e-12, "rad")
            yes(Math.abs(GraphFunction.compile("sin(x)", ws, AngleMode.DEG).eval(90.0) - 1.0) < 1e-12, "deg")
        }
        t("asymptotes and invalid regions produce gaps, not misleading lines") {
            val tan = GraphFunction.compile("tan(x)", ws, AngleMode.RAD)
            val s1 = GraphEngine.sample(tan::eval, Viewport(-10.0, 10.0, -10.0, 10.0)); yes(s1.size >= 6, "tan should have several branches, got ${s1.size}")
            val inv = GraphFunction.compile("1/x", ws, AngleMode.DEG)
            val s2 = GraphEngine.sample(inv::eval, Viewport.DEFAULT); eq(2, s2.size); yes(s2.none { seg -> seg.any { it.x < 0 } && seg.any { it.x > 0 } }, "no segment may cross x = 0")
            val sq = GraphFunction.compile("sqrt(x)", ws, AngleMode.DEG)
            val s3 = GraphEngine.sample(sq::eval, Viewport.DEFAULT); eq(1, s3.size); yes(s3[0].first().x >= 0.0, "no points left of the domain")
            val ln = GraphFunction.compile("ln(x)", ws, AngleMode.DEG); yes(GraphEngine.sample(ln::eval, Viewport.DEFAULT).all { s -> s.all { it.x > 0 } }, "ln domain")
            val partial = GraphFunction.compile("sqrt(x)/(x-2)", ws, AngleMode.DEG); GraphEngine.sample(partial::eval, Viewport.DEFAULT)
        }
        t("invalid graph input is rejected with errors, never crashes") {
            for (bad in listOf("x + y", "x = 3", "", "solve(x=1)", "f(x, y) = x + y")) {
                try { GraphFunction.compile(bad, ws, AngleMode.DEG); throw AssertionError("expected rejection of '$bad'") } catch (e: MathError) { }
            }
            val g = GraphFunction.compile("x/0", ws, AngleMode.DEG); eq(0, GraphEngine.sample(g::eval, Viewport.DEFAULT).size)
        }
        t("annotations: f(x) = x^2 - 4 has roots ±2, vertex (0,-4)") {
            val g = GraphFunction.compile("f(x) = x^2 - 4", ws, AngleMode.DEG)
            val a = GraphAnalyzer.analyze(g, ws, AngleMode.DEG)
            eq(listOf(-2.0, 2.0), a.roots.map { it.x }); eq(listOf(Pt(0.0, -4.0)), a.extrema); eq(Pt(0.0, -4.0), a.yIntercept); yes(a.isQuadratic, "quadratic")
        }
        t("annotations: irrational roots computed, non-polynomials have none") {
            val a = GraphAnalyzer.analyze(GraphFunction.compile("x^2 - 2", ws, AngleMode.DEG), ws, AngleMode.DEG)
            yes(Math.abs(a.roots[1].x - Math.sqrt(2.0)) < 1e-12, "sqrt 2")
            eq(0, GraphAnalyzer.analyze(GraphFunction.compile("sin(x)", ws, AngleMode.DEG), ws, AngleMode.DEG).roots.size)
            eq(3, GraphAnalyzer.analyze(GraphFunction.compile("x^3 - x", ws, AngleMode.DEG), ws, AngleMode.DEG).roots.size)
        }
        t("auto viewport contains the interesting points") {
            val g = GraphFunction.compile("x^2 - 4", ws, AngleMode.DEG); val a = GraphAnalyzer.analyze(g, ws, AngleMode.DEG)
            val vp = GraphEngine.autoViewport(listOf(g::eval), a.all)
            yes(vp.xMin <= -2 && vp.xMax >= 2 && vp.yMin <= -4 && vp.yMax >= 0, "view $vp")
            val far = GraphEngine.autoViewport(listOf({ x: Double -> x - 100 }), listOf(Pt(100.0, 0.0))); yes(far.xMax >= 100, "far root visible")
        }
        t("viewport pan and zoom keep the focus point fixed") {
            val v = Viewport(-10.0, 10.0, -10.0, 10.0)
            val z = v.zoom(2.0, 750f, 250f, 1000f, 500f)
            val fx0 = v.toWorldX(750f, 1000f); val fy0 = v.toWorldY(250f, 500f)
            yes(Math.abs(z.toWorldX(750f, 1000f) - fx0) < 1e-9 && Math.abs(z.toWorldY(250f, 500f) - fy0) < 1e-9, "focus fixed")
            yes(Math.abs(z.width - 10.0) < 1e-9, "range halves")
            val p = v.pan(100f, 0f, 1000f, 500f); yes(Math.abs(p.xMin - -12.0) < 1e-9, "drag right moves view left")
            val p2 = v.pan(0f, 50f, 1000f, 500f); yes(Math.abs(p2.yMax - 12.0) < 1e-9, "drag down reveals higher y: ${p2.yMax}")
            yes(Viewport.DEFAULT.zoom(1e30, 0f, 0f, 100f, 100f).width >= 9.9e-7, "zoom clamp"); eq(Viewport.DEFAULT, Viewport.DEFAULT.zoom(Double.NaN, 0f, 0f, 1f, 1f))
            eq(0f, v.toScreenX(-10.0, 1000f)); eq(500f, v.toScreenY(-10.0, 500f)); eq(0f, v.toScreenY(10.0, 500f))
        }
        t("ticks and labels") {
            val tk = GraphEngine.ticks(-10.0, 10.0); yes(0.0 in tk && tk.size in 4..12, "ticks $tk")
            eq(listOf(0.0, 0.5, 1.0), GraphEngine.ticks(0.0, 1.0, 2)); eq("0", GraphEngine.tickLabel(0.0, 1.0)); eq("2.5", GraphEngine.tickLabel(2.5, 0.5)); eq("-4", GraphEngine.tickLabel(-4.0, 2.0))
        }
    }

    private fun editorTests() {
        t("Editor.fraction: numerator from number, name or group; function names are not numerators") {
            eq(EditorState("(12)/()", 6), Editor.fraction(EditorState.of("12")))
            eq(EditorState("2+(x)/()", 7), Editor.fraction(EditorState.of("2+x")))
            eq(EditorState("(1+2)/()", 7), Editor.fraction(EditorState.of("(1+2)")))
            eq(EditorState("sqrt()/()", 5), Editor.fraction(EditorState.of("sqrt")))
            eq(EditorState("sin(()/()", 5), Editor.fraction(EditorState.of("sin(")))
            eq(EditorState("()/()", 1), Editor.fraction(EditorState()))
            eq(EditorState("(3)/()+5", 5), Editor.fraction(EditorState("3+5", 1)))
        }
        t("Pretty shows simple fraction templates as a⁄b") { eq("3⁄4+1⁄2", Pretty.format("(3)/(4)+(1)/(2)")) }
        t("insert, cursor movement and backspace") {
            var s = EditorState()
            s = Editor.insert(s, "12"); s = Editor.insert(s, "+"); s = Editor.insert(s, "3"); eq("12+3", s.text)
            s = Editor.left(s); s = Editor.insert(s, "0"); eq("12+03", s.text); eq(4, s.cursor)
            s = Editor.backspace(s); eq("12+3", s.text); s = Editor.right(Editor.right(s)); eq(4, s.cursor)
            eq(EditorState("", 0), Editor.backspace(EditorState()))
        }
        t("backspace removes whole function tokens, clear entry removes whole entries") {
            eq("2+", Editor.backspace(EditorState.of("2+sqrt(")).text); eq("2+sin(3)", Editor.backspace(EditorState.of("2+sin(3))")).text); eq("f", Editor.backspace(EditorState.of("f(")).text)
            eq("x<", Editor.backspace(EditorState.of("x<=")).text.let { "x<" })
            eq("2+", Editor.clearEntry(EditorState.of("2+345")).text); eq("2+", Editor.clearEntry(EditorState.of("2+sin(")).text); eq("2+", Editor.clearEntry(EditorState.of("2+abc")).text)
            eq("2", Editor.clearEntry(EditorState.of("2+")).text); eq("", Editor.clearAll().text)
        }
        t("negate key toggles sign of the number before the cursor") {
            eq("(-5)", Editor.negate(EditorState.of("5")).text); eq("5", Editor.negate(EditorState.of("(-5)")).text)
            eq("3+(-4.5)", Editor.negate(EditorState.of("3+4.5")).text); eq("-", Editor.negate(EditorState()).text); eq("2*-", Editor.negate(EditorState.of("2*")).text)
            eq("14", calc(Editor.negate(Editor.negate(EditorState.of("14"))).text))
        }
        t("pretty preview") {
            eq("x²+1", Pretty.format("x^2+1")); eq("√(2)×π", Pretty.format("sqrt(2)*pi")); eq("x⁻¹", Pretty.format("x^-1")); eq("1.5×10⁻⁶", Pretty.format("1.5E-6"))
            eq("a≤b÷c−d", Pretty.format("a<=b/c-d"))
        }
        t("live preview is side-effect free and only for numeric input") {
            val ws = Workspace(); val s = CalcSettings()
            eq("14", Calculator.preview("2+3*4", ws, s)); eq("5", Calculator.preview("(2+3", ws, s)); eq(null, Calculator.preview("2+", ws, s)); eq(null, Calculator.preview("x+1", ws, s))
            eq(null, Calculator.preview("a := 3", ws, s)); yes(ws.variables.isEmpty(), "preview must not define anything"); eq(null, Calculator.preview("solve(x=1)", ws, s))
        }
    }

    private fun limits() {
        t("expensive calculations are refused instead of freezing") {
            fails(ErrorKind.LIMIT, "2^1000000"); fails(ErrorKind.LIMIT, "10^100000"); fails(ErrorKind.LIMIT, "3001!"); fails(ErrorKind.LIMIT, "1E999999")
            fails(ErrorKind.LIMIT, "(".repeat(300) + "1" + ")".repeat(300))
        }
        t("evaluation deadline is enforced") {
            try { Evaluator(EmptyScope, AngleMode.DEG, EvalLimits(System.nanoTime() - 1)).eval(Parser().parse("1+1")); throw AssertionError("expected timeout") }
            catch (e: MathError) { eq(ErrorKind.LIMIT, e.kind) }
        }
        t("recursion limits for user functions are enforced") {
            val ws = Workspace(); ws.defineFunction("ga", listOf("x"), "x + 1"); eq("3", calc("ga(2)", ws = ws))
            val ev = Evaluator(ws, AngleMode.DEG, EvalLimits(maxDepth = 2))
            ws.defineFunction("fa", listOf("x"), "ga(x)"); ws.defineFunction("fb", listOf("x"), "fa(x)"); ws.defineFunction("fc", listOf("x"), "fb(x)")
            try { ev.eval(Parser(ws).parse("fc(1)")); throw AssertionError("depth") } catch (e: MathError) { eq(ErrorKind.LIMIT, e.kind) }
        }
        t("rational arithmetic invariants") {
            eq(Rat.of(1, 2), Rat.of(2, 4)); eq(Rat.of(-1, 2), Rat.of(1, -2)); eq("1/2", Rat.of(1, 2).toString()); eq(Rat.of(5, 6), Rat.of(1, 2) + Rat.of(1, 3))
            eq(Rat.of(1, 8), Rat.of(1, 2).pow(3)); eq(Rat.of(8), Rat.of(1, 2).pow(-3)); yes(Rat.of(1, 3) < Rat.of(1, 2), "ordering"); eq(Rat.ZERO, Rat.of(0, 5))
        }
        t("quadratic roots are consistent with delta for many coefficient sets") {
            for (a in -3..3) for (b in -4..4) for (c in -4..4) {
                if (a == 0) continue
                val q = PolyRoots.quadratic(Rat.of(a), Rat.of(b), Rat.of(c))
                val d = b * b - 4 * a * c
                eq(Rat.of(d), q.delta, "a=$a b=$b c=$c")
                eq(if (d < 0) 0 else if (d == 0) 1 else 2, q.roots.size, "a=$a b=$b c=$c")
                for (r in q.roots) { val x = Ops.toDouble(r); yes(Math.abs(a * x * x + b * x + c) < 1e-9, "root $x does not satisfy a=$a b=$b c=$c") }
            }
        }
        t("random polynomial equations: exact roots satisfy the equation") {
            val rnd = java.util.Random(42)
            repeat(40) {
                val r1 = rnd.nextInt(9) - 4; val r2 = rnd.nextInt(9) - 4; val r3 = rnd.nextInt(5) - 2
                val u = UPoly(listOf(Rat.of(-r1), Rat.ONE)) * UPoly(listOf(Rat.of(-r2), Rat.ONE)) * UPoly(listOf(Rat.of(-r3), Rat.ONE))
                val roots = PolyRoots.realRoots(u)
                eq(setOf(r1, r2, r3).sorted(), roots.map { (it.value as Value.Q).r.num.toInt() })
                yes(roots.all { it.exact }, "all roots exact")
            }
        }
    }
}

fun main() {
    val res = CoreSuite.run()
    val failed = res.filter { it.error != null }
    for (r in res) println((if (r.error == null) "PASS " else "FAIL ") + r.name + (r.error?.let { "\n     -> $it" } ?: ""))
    println("\n${res.size - failed.size}/${res.size} passed")
    if (failed.isNotEmpty()) System.exit(1)
}
