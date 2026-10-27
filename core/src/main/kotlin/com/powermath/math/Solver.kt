package com.powermath.math

import java.math.BigDecimal
import java.math.BigInteger

class Solver(private val scope: Scope, private val angle: AngleMode = AngleMode.DEG) {
    private val builder = PolyBuilder(scope, angle)

    private fun fx(v: Value) = Formatter.exact(v)
    private fun poly(l: Expr, r: Expr): MPoly = builder.build(l) - builder.build(r)

    private fun pickVar(p: MPoly, prefer: String?): String? {
        val vs = p.vars().toList()
        if (prefer != null) {
            if (vs.isEmpty()) return null
            if (vs == listOf(prefer)) return prefer
            throw MathError(ErrorKind.UNSUPPORTED, "Cannot solve for '$prefer': the equation contains ${vs.joinToString(", ")}. Give a system with enough equations or assign values first.")
        }
        return when (vs.size) {
            0 -> null
            1 -> vs[0]
            else -> throw MathError(ErrorKind.UNSUPPORTED, "Several unknowns (${vs.joinToString(", ")}). Use a system of equations, or assign values to all but one variable.")
        }
    }

    // ---------------- equations ----------------
    fun solveEquation(l: Expr, r: Expr, prefer: String? = null): Outcome {
        val diff = try { poly(l, r) } catch (e: MathError) {
            if (e.kind == ErrorKind.UNSUPPORTED && hasVarSqrt(l, r)) return solveRadical(RelOp.EQ, l, r, prefer)
            if (e.kind == ErrorKind.UNSUPPORTED && hasVarDivision(l, r)) return solveRationalEquation(l, r, prefer)
            throw e
        }
        val v = pickVar(diff, prefer)
        if (v == null) {
            return if (diff.constantValue().isZero)
                Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("Identity: true for every real value")))
            else Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("No solution: the equation is false")))
        }
        val u = UPoly.fromM(diff, v)
        return solveUnivariate(u, v)
    }

    fun solveUnivariate(u: UPoly, v: String, extraLines: List<Line> = emptyList(), extraSteps: List<String> = emptyList()): Outcome {
        val steps = ArrayList<String>(extraSteps)
        if (u.isZero) return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("Infinitely many solutions: every real $v works")), steps)
        val polyText = u.toM(v).toText()
        if (u.degree == 0) return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("No solution: the equation reduces to ${polyText} = 0, which is false")), steps)
        steps.add("Move everything to one side: $polyText = 0")
        val lines = ArrayList<Line>()
        if (u.degree == 2) {
            val q = PolyRoots.quadratic(u.c[2], u.c[1], u.c[0])
            steps.add("a = ${q.a}, b = ${q.b}, c = ${q.c}")
            steps.add("Δ = b² − 4ac = (${q.b})² − 4·(${q.a})·(${q.c}) = ${q.delta}")
            when {
                q.delta.signum > 0 -> {
                    steps.add("Δ > 0: two distinct real roots  $v = (−b ± √Δ) / (2a)")
                    q.roots.forEach { lines.add(Line.Eq(v, it)) }
                    lines.add(Line.Text("Δ = ${q.delta}"))
                }
                q.delta.isZero -> {
                    steps.add("Δ = 0: one repeated real root  $v = −b / (2a)")
                    lines.add(Line.Eq(v, q.roots[0]))
                    lines.add(Line.Text("Δ = 0 (repeated root)"))
                }
                else -> {
                    steps.add("Δ < 0: the parabola does not cross the $v-axis, so there are no real roots")
                    lines.add(Line.Text("No real solutions (Δ = ${q.delta} < 0)"))
                    val re = -q.b / (Rat.of(2) * q.a)
                    val im = Ops.div(Ops.sqrtRat(-q.delta), Value.Q((Rat.of(2) * q.a).abs()))
                    val imText = if (im == Value.ONE) "i" else "${fx(im)}·i"
                    lines.add(Line.Text("Complex roots: $v = ${if (re.isZero) "±" + imText else "$re ± $imText"}"))
                }
            }
        } else {
            val roots = PolyRoots.realRoots(u)
            if (u.degree == 1) steps.add("Linear equation: $v = ${fx(roots[0].value)}")
            else steps.add("Rational root test, then exact quadratic formula on what remains; other real roots are found numerically")
            if (roots.isEmpty()) lines.add(Line.Text("No real solutions"))
            roots.forEach { lines.add(Line.Eq(v, it.value)) }
            if (roots.any { !it.exact }) lines.add(Line.Text("Values marked ≈ are numerical approximations"))
        }
        lines.addAll(extraLines)
        return Outcome(OutcomeKind.SOLUTION, lines, steps, graphExpr = polyText)
    }

    /** a x^2 + b x + c = 0 with vertex information. */
    fun quadraticTool(a: Rat, b: Rat, c: Rat): Outcome {
        val u = UPoly(listOf(c, b, a))
        val extra = ArrayList<Line>()
        val steps = ArrayList<String>()
        if (a.isZero) steps.add("a = 0, so this is not quadratic; solving the linear equation instead")
        else {
            val xv = -b / (Rat.of(2) * a)
            val yv = c - b * b / (Rat.of(4) * a)
            extra.add(Line.Text("Vertex: ($xv, $yv)"))
            extra.add(Line.Text("Axis of symmetry: x = $xv"))
        }
        return solveUnivariate(u, "x", extra, steps)
    }

    // ---------------- inequalities ----------------
    private fun ratFromDouble(d: Double): Rat {
        val bd = BigDecimal(d)
        return if (bd.scale() >= 0) Rat.of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
        else Rat.of(bd.unscaledValue().multiply(BigInteger.TEN.pow(-bd.scale())), BigInteger.ONE)
    }

    private fun satisfied(op: RelOp, sign: Int) = when (op) {
        RelOp.LT -> sign < 0; RelOp.GT -> sign > 0; RelOp.LE -> sign <= 0; RelOp.GE -> sign >= 0; RelOp.NE -> sign != 0
        RelOp.EQ -> sign == 0
    }

    fun solveInequality(op: RelOp, l: Expr, r: Expr, prefer: String? = null): Outcome {
        if (op == RelOp.EQ) return solveEquation(l, r, prefer)
        val diff = try { poly(l, r) } catch (e: MathError) {
            if (e.kind == ErrorKind.UNSUPPORTED && hasVarSqrt(l, r)) return solveRadical(op, l, r, prefer)
            if (e.kind == ErrorKind.UNSUPPORTED && hasVarDivision(l, r)) return solveRationalInequality(op, l, r, prefer)
            throw e
        }
        val v = pickVar(diff, prefer)
        if (v == null) {
            val s = diff.constantValue().signum
            return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text(if (satisfied(op, s)) "True for every real value" else "No solution: the inequality is false")))
        }
        val u = UPoly.fromM(diff, v)
        val opText = op.symbol
        val steps = arrayListOf("Move everything to one side: ${u.toM(v).toText()} $opText 0")
        val roots = PolyRoots.realRoots(u)
        steps.add(if (roots.isEmpty()) "The polynomial has no real roots, so its sign never changes"
        else "Real roots (sign can only change here): ${roots.joinToString(", ") { fx(it.value) }}")
        val k = roots.size
        val rd = roots.map { it.d }
        // elements: interval0, root0, interval1, root1, ..., interval k
        val sat = ArrayList<Boolean>()
        for (i in 0..k) {
            val sample = when {
                k == 0 -> Rat.ZERO
                i == 0 -> ratFromDouble(rd[0] - 1.0)
                i == k -> ratFromDouble(rd[k - 1] + 1.0)
                else -> ratFromDouble((rd[i - 1] + rd[i]) / 2.0)
            }
            sat.add(satisfied(op, u.eval(sample).signum))
            if (i < k) sat.add(satisfied(op, 0))
        }
        steps.add("Test the sign on each interval and at each root, keeping those that satisfy $opText 0")
        val ivs = ArrayList<Line.Iv>()
        var e = 0
        while (e < sat.size) {
            if (!sat[e]) { e++; continue }
            val start = e
            while (e + 1 < sat.size && sat[e + 1]) e++
            val end = e
            val lo: Value? = if (start == 0) null else if (start % 2 == 0) roots[start / 2 - 1].value else roots[(start - 1) / 2].value
            val loClosed = start % 2 == 1
            val hi: Value? = if (end == sat.size - 1) null else if (end % 2 == 0) roots[end / 2].value else roots[(end - 1) / 2].value
            val hiClosed = end % 2 == 1
            ivs.add(Line.Iv(lo, loClosed, hi, hiClosed))
            e++
        }
        val lines: List<Line> = when {
            ivs.isEmpty() -> listOf(Line.Text("No real solutions"))
            ivs.size == 1 && ivs[0].lo == null && ivs[0].hi == null -> listOf(Line.Text("All real numbers"))
            else -> listOf(Line.IntervalSet(v, ivs))
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps, graphExpr = u.toM(v).toText())
    }

    // ---------------- rational expressions: variables in denominators ----------------
    private fun hasVarDivision(vararg es: Expr): Boolean {
        fun walk(e: Expr): Boolean = when (e) {
            is Expr.Bin -> (e.op == '/' && ExprUtil.freeVars(e.r).any { scope.variable(it) == null }) ||
                (e.op == '^' && e.r is Expr.Neg) || walk(e.l) || walk(e.r)
            is Expr.Neg -> walk(e.e)
            is Expr.Percent -> walk(e.e)
            is Expr.Call -> e.args.any { walk(it) }
            else -> false
        }
        return es.any { walk(it) }
    }

    /** Numerator and denominator of l - r as polynomials. */
    private fun fraction(l: Expr, r: Expr): Pair<MPoly, MPoly> {
        val (ln, ld) = frac(l); val (rn, rd) = frac(r)
        return Pair(ln * rd - rn * ld, ld * rd)
    }

    private fun frac(e: Expr): Pair<MPoly, MPoly> {
        val one = MPoly.const(Rat.ONE)
        return when {
            e is Expr.Neg -> frac(e.e).let { Pair(-it.first, it.second) }
            e is Expr.Bin && e.op == '/' -> {
                val (a, b) = frac(e.l); val (c, d) = frac(e.r)
                if (c.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                Pair(a * d, b * c)
            }
            e is Expr.Bin && (e.op == '+' || e.op == '-') && e.r !is Expr.Percent -> {
                val (a, b) = frac(e.l); val (c, d) = frac(e.r)
                if (e.op == '+') Pair(a * d + c * b, b * d) else Pair(a * d - c * b, b * d)
            }
            e is Expr.Bin && e.op == '*' -> { val (a, b) = frac(e.l); val (c, d) = frac(e.r); Pair(a * c, b * d) }
            e is Expr.Bin && e.op == '^' -> {
                val k = try { builder.build(e.r) } catch (ex: MathError) { null }
                if (k == null || !k.isConstant || !k.constantValue().isInteger || k.constantValue().num.bitLength() > 6)
                    throw MathError(ErrorKind.UNSUPPORTED, "Powers in fractions must be whole numbers")
                val n = k.constantValue().num.toInt()
                val (a, b) = frac(e.l)
                if (n >= 0) Pair(a.pow(n), b.pow(n)) else { if (a.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero"); Pair(b.pow(-n), a.pow(-n)) }
            }
            else -> Pair(builder.build(e), one)
        }
    }

    private fun pickVarN(vs: Set<String>, prefer: String?): String? {
        if (prefer != null) {
            if (vs.isEmpty()) return null
            if (vs == setOf(prefer)) return prefer
            throw MathError(ErrorKind.UNSUPPORTED, "Cannot solve for '$prefer': the equation contains ${vs.joinToString(", ")}.")
        }
        return when (vs.size) {
            0 -> null
            1 -> vs.first()
            else -> throw MathError(ErrorKind.UNSUPPORTED, "Several unknowns (${vs.joinToString(", ")}). Assign values to all but one variable.")
        }
    }

    private fun zeroAt(d: UPoly, value: Value): Boolean =
        if (value is Value.Q) d.eval(value.r).isZero else Math.abs(d.evalD(Ops.toDouble(value))) < 1e-9 * (1.0 + Math.abs(Ops.toDouble(value)))

    fun solveRationalEquation(l: Expr, r: Expr, prefer: String? = null): Outcome {
        val (n, d) = fraction(l, r)
        val v = pickVarN(n.vars() + d.vars(), prefer) ?: throw MathError(ErrorKind.UNSUPPORTED, "Nothing to solve for")
        if (d.vars().isEmpty() && n.vars().isEmpty()) throw MathError(ErrorKind.UNSUPPORTED, "Nothing to solve for")
        val un = UPoly.fromM(n, v); val ud = UPoly.fromM(d, v)
        val steps = arrayListOf("Multiply through by the denominators: ${un.toM(v).toText()} = 0", "Excluded values (a denominator would be 0): ${if (ud.degree == 0) "none" else ud.toM(v).toText() + " = 0"}")
        val inner = solveUnivariate(un, v)
        val out = ArrayList<Line>()
        var removed = 0
        for (line in inner.lines) {
            if (line is Line.Eq && zeroAt(ud, line.value)) { removed++; steps.add("$v = ${fx(line.value)} makes a denominator 0, so it is rejected") } else out.add(line)
        }
        val kept = out.any { it is Line.Eq }
        val lines: List<Line> = when {
            un.isZero -> listOf(Line.Text("True for every real $v except where a denominator is 0"))
            removed > 0 && !kept -> listOf(Line.Text("No solution: every candidate makes a denominator 0"))
            removed > 0 -> out.filter { !(it is Line.Text && (it.text.startsWith("Δ") || it.text.startsWith("Complex"))) }
            else -> out
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps + inner.steps.drop(0), graphExpr = null)
    }

    fun solveRationalInequality(op: RelOp, l: Expr, r: Expr, prefer: String? = null): Outcome {
        val (n, d) = fraction(l, r)
        val v = pickVarN(n.vars() + d.vars(), prefer)
        if (v == null) {
            val s = n.constantValue().signum * d.constantValue().signum
            return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text(if (satisfied(op, s)) "True for every real value" else "No solution: the inequality is false")))
        }
        val un = UPoly.fromM(n, v); val ud = UPoly.fromM(d, v)
        val steps = arrayListOf("Move everything to one side and combine into one fraction: (${un.toM(v).toText()}) / (${ud.toM(v).toText()}) ${op.symbol} 0")
        val rn = if (un.isZero) emptyList() else PolyRoots.realRoots(un)
        val rd = if (ud.degree <= 0) emptyList() else PolyRoots.realRoots(ud)
        steps.add("The sign can only change at roots of the numerator or the denominator; denominator roots are always excluded")
        // merge critical points
        class Crit(val value: Value, val d: Double, val isDen: Boolean)
        val all = (rn.map { Crit(it.value, it.d, false) } + rd.map { Crit(it.value, it.d, true) }).sortedBy { it.d }
        val crit = ArrayList<Crit>()
        for (c in all) {
            val last = crit.lastOrNull()
            if (last != null && Math.abs(last.d - c.d) < 1e-12 * (1 + Math.abs(c.d))) { if (c.isDen && !last.isDen) { crit.removeAt(crit.size - 1); crit.add(c) } }
            else crit.add(c)
        }
        val k = crit.size
        fun signAt(x: Double): Int { val a = un.evalD(x); val b = ud.evalD(x); val q = a / b; return if (q > 0) 1 else if (q < 0) -1 else 0 }
        val sat = ArrayList<Boolean>()
        for (i in 0..k) {
            val sample = when { k == 0 -> 0.0; i == 0 -> crit[0].d - 1.0; i == k -> crit[k - 1].d + 1.0; else -> (crit[i - 1].d + crit[i].d) / 2.0 }
            sat.add(satisfied(op, signAt(sample)))
            if (i < k) sat.add(!crit[i].isDen && satisfied(op, 0))
        }
        val ivs = ArrayList<Line.Iv>()
        var e = 0
        while (e < sat.size) {
            if (!sat[e]) { e++; continue }
            val start = e
            while (e + 1 < sat.size && sat[e + 1]) e++
            val end = e
            val lo: Value? = if (start == 0) null else crit[if (start % 2 == 0) start / 2 - 1 else (start - 1) / 2].value
            val hi: Value? = if (end == sat.size - 1) null else crit[if (end % 2 == 0) end / 2 else (end - 1) / 2].value
            ivs.add(Line.Iv(lo, start % 2 == 1, hi, end % 2 == 1))
            e++
        }
        val lines: List<Line> = when {
            ivs.isEmpty() -> listOf(Line.Text("No real solutions"))
            ivs.size == 1 && ivs[0].lo == null && ivs[0].hi == null -> listOf(Line.Text("All real numbers"))
            else -> listOf(Line.IntervalSet(v, ivs))
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps)
    }

    // ---------------- square roots of expressions containing the unknown ----------------
    private fun hasVarSqrt(vararg es: Expr): Boolean {
        fun walk(e: Expr): Boolean = when (e) {
            is Expr.Call -> (e.name == "sqrt" && ExprUtil.freeVars(e.args[0]).any { scope.variable(it) == null }) || e.args.any { walk(it) }
            is Expr.Bin -> walk(e.l) || walk(e.r)
            is Expr.Neg -> walk(e.e)
            is Expr.Percent -> walk(e.e)
            else -> false
        }
        return es.any { walk(it) }
    }

    private class Radicals { val names = ArrayList<String>(); val radicands = ArrayList<MPoly>() }

    /** (numerator, denominator) as polynomials in x and in one new symbol per distinct sqrt(...) (_r1, _r2, ...). */
    private fun radFrac(e: Expr, rad: Radicals): Pair<MPoly, MPoly> {
        val one = MPoly.const(Rat.ONE)
        return when {
            e is Expr.Call && e.name == "sqrt" && e.args.size == 1 && ExprUtil.freeVars(e.args[0]).any { scope.variable(it) == null } -> {
                val (inn, innDen) = radFrac(e.args[0], rad)
                if (innDen.vars().isNotEmpty() || innDen.constantValue() != Rat.ONE && innDen.isConstant.not()) throw MathError(ErrorKind.UNSUPPORTED, "A fraction under a square root is not supported")
                val inner = if (innDen.isConstant) inn.scale(innDen.constantValue().inverse()) else inn
                if (inner.vars().any { it.startsWith("_r") }) throw MathError(ErrorKind.UNSUPPORTED, "A square root inside another square root is not supported")
                val idx = rad.radicands.indexOfFirst { it.terms == inner.terms }
                val sym = if (idx >= 0) MPoly.variable(rad.names[idx]) else {
                    if (rad.names.size >= 3) throw MathError(ErrorKind.UNSUPPORTED, "At most three different square roots are supported in one equation")
                    rad.names.add("_r" + (rad.names.size + 1)); rad.radicands.add(inner); MPoly.variable(rad.names.last())
                }
                Pair(sym, one)
            }
            e is Expr.Neg -> radFrac(e.e, rad).let { Pair(-it.first, it.second) }
            e is Expr.Bin && (e.op == '+' || e.op == '-') && e.r !is Expr.Percent -> {
                val (a, b) = radFrac(e.l, rad); val (c, d) = radFrac(e.r, rad)
                if (e.op == '+') Pair(a * d + c * b, b * d) else Pair(a * d - c * b, b * d)
            }
            e is Expr.Bin && e.op == '*' -> { val (a, b) = radFrac(e.l, rad); val (c, d) = radFrac(e.r, rad); Pair(a * c, b * d) }
            e is Expr.Bin && e.op == '/' -> {
                val (a, b) = radFrac(e.l, rad); val (c, d) = radFrac(e.r, rad)
                if (c.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                Pair(a * d, b * c)
            }
            e is Expr.Bin && e.op == '^' -> {
                val k = builder.build(e.r)
                if (!k.isConstant || !k.constantValue().isInteger || k.constantValue().num.bitLength() > 5)
                    throw MathError(ErrorKind.UNSUPPORTED, "Powers next to square roots must be whole numbers")
                val n = k.constantValue().num.toInt()
                val (a, b) = radFrac(e.l, rad)
                if (n >= 0) Pair(a.pow(n), b.pow(n)) else { if (a.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero"); Pair(b.pow(-n), a.pow(-n)) }
            }
            else -> Pair(builder.build(e), one)
        }
    }

    /** Replaces s^2 by the radicand, so every symbol appears with exponent 0 or 1. */
    private fun reduceRad(p: MPoly, rad: Radicals): MPoly {
        var acc = MPoly.ZERO
        for ((mono, c) in p.terms) {
            var factor = MPoly.const(c)
            val keep = ArrayList<Pair<String, Int>>()
            for ((n, ex) in mono.vars) {
                val idx = rad.names.indexOf(n)
                if (idx < 0) keep.add(Pair(n, ex))
                else { factor = factor * rad.radicands[idx].pow(ex / 2); if (ex % 2 == 1) keep.add(Pair(n, 1)) }
            }
            acc += factor * MPoly(mapOf(Mono(keep.sortedBy { it.first }) to Rat.ONE))
        }
        return acc
    }

    /** Squares the roots away one at a time: A + B s = 0  gives  A^2 - B^2 p = 0. */
    private fun eliminateRoots(p0: MPoly, rad: Radicals, steps: MutableList<String>): MPoly {
        var cur = reduceRad(p0, rad)
        for (i in rad.names.indices.reversed()) {
            val name = rad.names[i]
            if (cur.degreeIn(name) == 0) continue
            val a = MPoly(cur.terms.filterKeys { it.exp(name) == 0 })
            val b = MPoly(cur.terms.filterKeys { it.exp(name) == 1 }.mapKeys { (m, _) -> Mono(m.vars.filter { it.first != name }) })
            cur = reduceRad(a * a - b * b * rad.radicands[i], rad)
            steps.add("Isolate the square root and square both sides: ${cur.toText()} = 0")
        }
        return cur
    }

    private fun evalSides(l: Expr, r: Expr, v: String, x: Value): Double? = try {
        val ev = Evaluator(scope, angle, EvalLimits(), mapOf(v to x))
        Ops.toDouble(ev.eval(l)) - Ops.toDouble(ev.eval(r))
    } catch (e: MathError) { null }

    fun solveRadical(op: RelOp, l: Expr, r: Expr, prefer: String? = null): Outcome {
        val rad = Radicals()
        val (ln, ld) = radFrac(l, rad); val (rn, rd) = radFrac(r, rad)
        val diff = ln * rd - rn * ld
        val den = ld * rd
        val xv = (diff.vars() + den.vars()).filter { !it.startsWith("_r") } + rad.radicands.flatMap { it.vars() }
        val v = pickVarN(xv.toSet(), prefer) ?: throw MathError(ErrorKind.UNSUPPORTED, "Nothing to solve for")
        val steps = arrayListOf<String>()
        val domain = rad.radicands.map { UPoly.fromM(it, v) }
        if (domain.isNotEmpty()) steps.add("The expression under a root must not be negative: " + domain.joinToString(", ") { it.toM(v).toText() + " ≥ 0" })
        val n = eliminateRoots(diff, rad, steps)
        val un = UPoly.fromM(n, v)
        val denPoly = if (den.vars().isEmpty()) null else eliminateRoots(den, rad, ArrayList()).let { UPoly.fromM(it, v) }
        if (denPoly != null) steps.add("Values that make a denominator 0 are excluded")
        val roots = if (un.isZero) emptyList() else PolyRoots.realRoots(un)

        if (op == RelOp.EQ) {
            if (un.isZero) return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("True for every $v where the roots are defined (${domain.joinToString(", ") { it.toM(v).toText() + " ≥ 0" }})")), steps)
            steps.add("Check every candidate in the original equation, because squaring can add false solutions")
            val good = ArrayList<RealRoot>()
            for (rt in roots) {
                val f = evalSides(l, r, v, rt.value)
                if (f != null && Math.abs(f) <= 1e-9 * (1.0 + Math.abs(rt.d))) good.add(rt)
                else steps.add("$v = ${fx(rt.value)} does not satisfy the equation, so it is rejected")
            }
            val lines = ArrayList<Line>()
            if (good.isEmpty()) lines.add(Line.Text("No real solutions"))
            good.forEach { lines.add(Line.Eq(v, it.value)) }
            if (good.any { !it.exact }) lines.add(Line.Text("Values marked ≈ are numerical approximations"))
            return Outcome(OutcomeKind.SOLUTION, lines, steps)
        }

        // inequality: the sign of l - r can only change at roots of the squared polynomial or where a root starts to exist
        steps.add("The sign can only change at the roots of the squared equation or at the edge of the domain")
        class Crit(val value: Value, val d: Double)
        val denRoots = if (denPoly != null && denPoly.degree >= 1) PolyRoots.realRoots(denPoly) else emptyList()
        val all = (roots.map { Crit(it.value, it.d) } + denRoots.map { Crit(it.value, it.d) } + domain.filter { it.degree >= 1 }.flatMap { PolyRoots.realRoots(it).map { rt -> Crit(rt.value, rt.d) } }).sortedBy { it.d }
        val crit = ArrayList<Crit>()
        for (c in all) { val last = crit.lastOrNull(); if (last == null || Math.abs(last.d - c.d) >= 1e-12 * (1 + Math.abs(c.d))) crit.add(c) }
        val k = crit.size
        fun signOf(f: Double?): Int? = if (f == null) null else if (Math.abs(f) < 1e-9) 0 else if (f > 0) 1 else -1
        val sat = ArrayList<Boolean>()
        for (i in 0..k) {
            val sample = when { k == 0 -> 0.0; i == 0 -> crit[0].d - 1.0; i == k -> crit[k - 1].d + 1.0; else -> (crit[i - 1].d + crit[i].d) / 2.0 }
            val sg = signOf(evalSides(l, r, v, Value.D(sample)))
            sat.add(sg != null && satisfied(op, sg))
            if (i < k) { val pg = signOf(evalSides(l, r, v, crit[i].value)); sat.add(pg != null && satisfied(op, pg)) }
        }
        val ivs = ArrayList<Line.Iv>()
        var e = 0
        while (e < sat.size) {
            if (!sat[e]) { e++; continue }
            val start = e
            while (e + 1 < sat.size && sat[e + 1]) e++
            val end = e
            val lo: Value? = if (start == 0) null else crit[if (start % 2 == 0) start / 2 - 1 else (start - 1) / 2].value
            val hi: Value? = if (end == sat.size - 1) null else crit[if (end % 2 == 0) end / 2 else (end - 1) / 2].value
            ivs.add(Line.Iv(lo, start % 2 == 1, hi, end % 2 == 1))
            e++
        }
        val lines: List<Line> = when {
            ivs.isEmpty() -> listOf(Line.Text("No real solutions"))
            ivs.size == 1 && ivs[0].lo == null && ivs[0].hi == null -> listOf(Line.Text("All real numbers"))
            else -> listOf(Line.IntervalSet(v, ivs))
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps)
    }

    // ---------------- chained comparisons: 4 < 2 + x < 6 ----------------
    private fun cmpBound(a: Value, b: Value) = Ops.compare(a, b)

    private fun ivsOf(o: Outcome): List<Line.Iv> {
        val first = o.lines.firstOrNull()
        if (first is Line.IntervalSet) return first.intervals
        val t = (first as? Line.Text)?.text ?: return emptyList()
        return if (t.startsWith("All real") || t.startsWith("True")) listOf(Line.Iv(null, false, null, false)) else emptyList()
    }

    private fun intersect(a: List<Line.Iv>, b: List<Line.Iv>): List<Line.Iv> {
        val out = ArrayList<Line.Iv>()
        for (x in a) for (y in b) {
            // lower bound: the larger one; when equal, closed only if both closed
            val lo: Value?; val loC: Boolean
            if (x.lo == null) { lo = y.lo; loC = y.loClosed }
            else if (y.lo == null) { lo = x.lo; loC = x.loClosed }
            else { val c = cmpBound(x.lo, y.lo)
                if (c > 0) { lo = x.lo; loC = x.loClosed } else if (c < 0) { lo = y.lo; loC = y.loClosed } else { lo = x.lo; loC = x.loClosed && y.loClosed } }
            val hi: Value?; val hiC: Boolean
            if (x.hi == null) { hi = y.hi; hiC = y.hiClosed }
            else if (y.hi == null) { hi = x.hi; hiC = x.hiClosed }
            else { val c = cmpBound(x.hi, y.hi)
                if (c < 0) { hi = x.hi; hiC = x.hiClosed } else if (c > 0) { hi = y.hi; hiC = y.hiClosed } else { hi = x.hi; hiC = x.hiClosed && y.hiClosed } }
            if (lo != null && hi != null) {
                val c = cmpBound(lo, hi)
                if (c > 0 || (c == 0 && !(loC && hiC))) continue
            }
            out.add(Line.Iv(lo, loC, hi, hiC))
        }
        return out
    }

    fun solveChain(terms: List<Expr>, ops: List<RelOp>, labels: List<String>, prefer: String? = null): Outcome {
        if (ops.any { it == RelOp.EQ }) throw MathError(ErrorKind.UNSUPPORTED, "A chain can use <, ≤, >, ≥ and ≠. For equalities write separate equations.")
        val vars = (0 until ops.size).flatMap { i ->
            try { poly(terms[i], terms[i + 1]).vars() } catch (e: MathError) {
                if (e.kind == ErrorKind.UNSUPPORTED && (hasVarSqrt(terms[i], terms[i + 1]) || hasVarDivision(terms[i], terms[i + 1])))
                    (ExprUtil.freeVars(terms[i]) + ExprUtil.freeVars(terms[i + 1])).filter { scope.variable(it) == null }.toSet()
                else throw e
            }
        }.toSortedSet()
        var v = prefer ?: vars.firstOrNull()
        if (prefer == null && vars.size > 1) throw MathError(ErrorKind.UNSUPPORTED, "A chained comparison needs one unknown, but it contains ${vars.joinToString(", ")}. Assign values to the others first.")
        val steps = arrayListOf<String>()
        steps.add("Split the chain into ${ops.size} inequalities: " + ops.indices.joinToString(" and ") { "${labels[it]} ${ops[it].symbol} ${labels[it + 1]}" })
        var acc: List<Line.Iv> = listOf(Line.Iv(null, false, null, false))
        for (i in ops.indices) {
            val o = solveInequality(ops[i], terms[i], terms[i + 1], v)
            val part = ivsOf(o)
            val shown = if (part.isEmpty()) "no real solutions" else Line.IntervalSet(v ?: "x", part).let { Outcome(OutcomeKind.SOLUTION, listOf(it)).render(FormatSettings()).first() }
            steps.add("${labels[i]} ${ops[i].symbol} ${labels[i + 1]}  gives  $shown")
            acc = intersect(acc, part)
        }
        steps.add("Keep only the values that satisfy every inequality (intersection)")
        val name = v ?: "x"
        val lines: List<Line> = when {
            acc.isEmpty() -> listOf(Line.Text("No real solutions"))
            acc.size == 1 && acc[0].lo == null && acc[0].hi == null -> listOf(Line.Text(if (v == null) "True" else "All real numbers"))
            else -> listOf(Line.IntervalSet(name, acc))
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps)
    }

    // ---------------- linear systems ----------------
    fun solveSystem(eqs: List<Expr.Rel>): Outcome {
        if (eqs.any { it.op != RelOp.EQ }) throw MathError(ErrorKind.UNSUPPORTED, "Systems of inequalities are not supported")
        val polys = eqs.map { poly(it.l, it.r) }
        val vars = polys.flatMap { it.vars() }.toSortedSet().toList()
        if (polys.any { it.totalDegree > 1 }) throw MathError(ErrorKind.UNSUPPORTED, "Only systems of linear equations are supported")
        val n = vars.size
        if (n == 0) {
            return if (polys.all { it.constantValue().isZero }) Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("All equations are identities")))
            else Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("No solution: inconsistent system")))
        }
        val m = polys.map { p ->
            val row = Array(n + 1) { Rat.ZERO }
            for ((mono, c) in p.terms) {
                if (mono.vars.isEmpty()) row[n] = -c else row[vars.indexOf(mono.vars[0].first)] = c
            }
            row
        }.toTypedArray()
        val steps = ArrayList<String>()
        steps.add("Write the system as a matrix and apply Gauss-Jordan elimination over the rationals")
        var rank = 0
        val pivotCols = ArrayList<Int>()
        for (col in 0 until n) {
            var pr = -1
            for (r in rank until m.size) if (!m[r][col].isZero) { pr = r; break }
            if (pr < 0) continue
            val t = m[pr]; m[pr] = m[rank]; m[rank] = t
            val inv = m[rank][col].inverse()
            for (j in 0..n) m[rank][j] = m[rank][j] * inv
            for (r in m.indices) if (r != rank && !m[r][col].isZero) {
                val f = m[r][col]
                for (j in 0..n) m[r][j] = m[r][j] - f * m[rank][j]
            }
            pivotCols.add(col); rank++
        }
        for (r in rank until m.size) if (!m[r][n].isZero) {
            steps.add("A row reduces to 0 = ${m[r][n]}: contradiction")
            return Outcome(OutcomeKind.SOLUTION, listOf(Line.Text("No solution: the system is inconsistent")), steps)
        }
        if (rank == n) {
            steps.add("Rank = number of unknowns ($n): unique solution")
            val lines = vars.indices.map { i -> Line.Eq(vars[i], Value.Q(m[i][n])) }
            return Outcome(OutcomeKind.SOLUTION, lines, steps)
        }
        val free = (0 until n).filter { it !in pivotCols }
        steps.add("Rank $rank < $n unknowns: infinitely many solutions; free variable(s): ${free.joinToString { vars[it] }}")
        val lines = ArrayList<Line>()
        lines.add(Line.Text("Infinitely many solutions (free: ${free.joinToString { vars[it] }})"))
        for ((ri, col) in pivotCols.withIndex()) {
            var expr = MPoly.const(m[ri][n])
            for (f in free) expr -= MPoly.variable(vars[f]).scale(m[ri][f])
            lines.add(Line.Text("${vars[col]} = ${expr.toText()}"))
        }
        return Outcome(OutcomeKind.SOLUTION, lines, steps)
    }

    // ---------------- algebra ----------------
    private fun graphOf(p: MPoly): String? = if (p.vars().size == 1) p.toText() else null

    fun expand(e: Expr): Outcome {
        val p = builder.build(e)
        val steps = ArrayList<String>()
        Identities.recognize(e)?.let { steps += "Special product: $it" }
        steps += "Multiply out all products and collect like terms"
        return Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text(p.toText())), steps, graphExpr = graphOf(p))
    }

    fun simplify(e: Expr): Outcome {
        try {
            val p = builder.build(e)
            if (p.isConstant) {
                val c = p.constantValue()
                return Outcome(OutcomeKind.VALUE, listOf(Line.Val(Value.Q(c))), value = Value.Q(c))
            }
            return Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text(p.toText())), listOf("Expand products and collect like terms"), graphExpr = graphOf(p))
        } catch (ex: MathError) {
            if (ex.kind == ErrorKind.UNSUPPORTED && ExprUtil.freeVars(e).none { scope.variable(it) == null }) {
                val v = Evaluator(scope, angle).eval(e)
                return Outcome(OutcomeKind.VALUE, listOf(Line.Val(v)), value = v)
            }
            throw ex
        }
    }

    fun factor(e: Expr): Outcome {
        val p = builder.build(e)
        val vs = p.vars()
        if (vs.isEmpty()) return Outcome(OutcomeKind.VALUE, listOf(Line.Val(Value.Q(p.constantValue()))), value = Value.Q(p.constantValue()))
        if (vs.size > 1) throw MathError(ErrorKind.UNSUPPORTED, "Factoring supports polynomials in one variable only")
        val v = vs.first()
        val u = UPoly.fromM(p, v)
        val (content, prim) = u.primitive()
        var rest = prim
        val parts = ArrayList<String>()
        if (u.degree >= 2) {
            for (r in PolyRoots.rationalRoots(prim).sorted()) {
                val lin = UPoly(listOf(Rat.of(r.num.negate(), BigInteger.ONE), Rat.of(r.den, BigInteger.ONE)))
                var mult = 0
                while (true) {
                    val (q, rem) = rest.divmod(lin)
                    if (!rem.isZero) break
                    rest = q; mult++
                }
                if (mult > 0) { val lt = lin.toM(v).toText(); parts.add((if (lt == v) lt else "($lt)") + if (mult > 1) "^$mult" else "") }
            }
        }
        val (k2, rest2) = rest.primitive()
        val total = content * k2
        val sb = StringBuilder()
        if (total == Rat.of(-1)) sb.append("-") else if (total != Rat.ONE) sb.append(if (total.isInteger) total.toString() else "(${total})")
        var note: String? = null
        val factors = ArrayList<String>(parts)
        if (rest2.degree >= 1) {
            factors.add(if (parts.isEmpty() && sb.isEmpty()) rest2.toM(v).toText() else "(${rest2.toM(v).toText()})")
            note = if (rest2.degree >= 4) "The remaining factor has degree ${rest2.degree} and may factor further (only rational linear factors are extracted)"
            else if (rest2.degree >= 2 && parts.isEmpty()) "No rational roots: irreducible over the rationals" else null
        }
        sb.append(factors.joinToString(""))
        if (sb.isEmpty()) sb.append("1")
        val lines = arrayListOf<Line>(Line.Text(sb.toString()))
        if (note != null) lines.add(Line.Text(note))
        return Outcome(OutcomeKind.ALGEBRA, lines, listOf("Pull out the common content, then find rational roots with the rational root theorem"), graphExpr = u.toM(v).toText())
    }
}
