package com.powermath.math

data class CalcSettings(
    val angle: AngleMode = AngleMode.DEG,
    val format: FormatSettings = FormatSettings(),
    val timeoutMs: Long = 5000
)

/** Single entry point used by the UI: parse -> resolve -> evaluate/solve -> structured Outcome. */
object Calculator {

    fun process(input: String, ws: Workspace, settings: CalcSettings): Outcome {
        val text = ExprUtil.balanceParens(input.trim())
        if (text.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Empty expression")
        plusMinusVariants(text)?.let { variants ->
            val first = Parser(ws).parseAll(variants[0])
            if (first.any { it is Expr.FuncDefStmt || it is Expr.Assign })
                throw MathError(ErrorKind.SYNTAX, "± cannot be used in a definition")
            val lines = ArrayList<Line>()
            for (v in variants) {
                val o = process(v, ws, settings)
                val only = o.lines.singleOrNull()
                when {
                    only is Line.Val -> lines += Line.Eq(v.trim(), only.value)
                    o.lines.all { it is Line.Text } -> o.lines.forEach { lines += Line.Text("$v → " + (it as Line.Text).text) }
                    else -> lines += o.lines
                }
            }
            return Outcome(OutcomeKind.ALGEBRA, lines, listOf("± gives one result with + and one with −"))
        }
        val stmts = Parser(ws).parseAll(text)
        for (st in stmts) rejectNestedCommands(st)
        if (stmts.size == 1) autoFunction(stmts[0], text, ws, settings)?.let { (rewritten, body) ->
            val out = process(rewritten, ws, settings)
            return out.copy(
                steps = listOf("The function notation was added automatically: $rewritten") + out.steps,
                graphExpr = body
            )
        }
        val limits = EvalLimits.withTimeoutMs(settings.timeoutMs)
        val solver = Solver(ws, settings.angle)
        if (stmts.size > 1) {
            if (stmts.all { it is Expr.Rel }) return solver.solveSystem(stmts.map { it as Expr.Rel })
            throw MathError(ErrorKind.SYNTAX, "Several statements are only allowed as a system of equations separated by ';'")
        }
        return when (val st = stmts[0]) {
            is Expr.FuncDefStmt -> {
                val ch = ws.defineFunction(st.name, st.params, st.bodySource, st.overwrite)
                Outcome(OutcomeKind.DEFINED, listOf(Line.Text("${st.name}(${st.params.joinToString(", ")}) = ${st.bodySource}  (saved)")),
                    graphExpr = null, changes = listOf(ch))
            }
            is Expr.Assign -> {
                val ch = ws.defineVariable(st.name, st.source, overwrite = true, angle = settings.angle)
                Outcome(OutcomeKind.DEFINED, listOf(Line.Eq(st.name, ch.def.value)), value = ch.def.value, changes = listOf(ch))
            }
            is Expr.Call -> if (st.name in Names.commands) command(st, text, ws, settings, solver, limits) else plain(st, ws, settings, solver, limits)
            is Expr.Rel -> relation(st, ws, settings, solver, limits)
            is Expr.Chain -> chain(st, ws, settings, solver, limits)
            else -> plain(st, ws, settings, solver, limits)
        }
    }

    private val autoNames = listOf("f", "g", "h", "j", "k", "m", "n", "o") + ('a'..'z').map { "f$it" }

    /**
     * A linear expression in one unknown without function notation ("2x + 3" or "y = 2x + 3") becomes "f(x) = 2x + 3".
     * Returns the rewritten statement and the body, or null when this does not apply.
     */
    private fun autoFunction(st: Expr, text: String, ws: Workspace, s: CalcSettings): Pair<String, String>? {
        val body: Expr
        val bodyText: String
        val yForm: Boolean
        when {
            st is Expr.Rel && st.op == RelOp.EQ && st.l is Expr.Var && st.l.name == "y" && ws.variable("y") == null && ws.function("y") == null -> {
                body = st.r; bodyText = text.substringAfter('=').trim(); yForm = true
            }
            st is Expr.Rel || st is Expr.Chain || st is Expr.Assign || st is Expr.FuncDefStmt -> return null
            st is Expr.Call && st.name in Names.commands -> return null
            else -> { body = st; bodyText = text.trim(); yForm = false }
        }
        val free = ExprUtil.freeVars(body).filter { ws.variable(it) == null && it != "ans" }.distinct()
        if (free.size != 1) return null
        if (bodyText.isEmpty() || ExprUtil.calledFunctions(body).any { it in Names.commands }) return null
        val v = free[0]
        val poly = try { PolyBuilder(ws, s.angle).build(body) } catch (e: MathError) { null }
        // "y = ..." is always a function. A bare expression becomes one when it is linear, or when it is not a
        // polynomial at all (x^1.1, sin(x), 2^x, sqrt(x) ...). Other polynomials keep being expanded.
        val applies = yForm || poly == null || (poly.vars() == setOf(v) && poly.degreeIn(v) == 1)
        if (!applies) return null
        val name = autoNames.firstOrNull { ws.function(it) == null && ws.variable(it) == null && it != v && it !in Names.reserved } ?: return null
        return Pair("$name($v) = $bodyText", bodyText)
    }

    /** "a ± b" gives both results. Unary "±b" means "+b" and "-b". Returns the variants, or null when there is no ±. */
    private fun plusMinusVariants(text: String): List<String>? {
        val count = text.count { it == '±' }
        if (count == 0) return null
        if (count > 3) throw MathError(ErrorKind.UNSUPPORTED, "At most three ± signs are supported in one calculation")
        var variants = listOf("")
        var i = 0
        val unaryAfter = "(,+-*/^=<>;:"
        while (i < text.length) {
            val ch = text[i]
            if (ch != '±') { variants = variants.map { it + ch }; i++; continue }
            val prev = text.substring(0, i).trimEnd().lastOrNull()
            val unary = prev == null || prev in unaryAfter
            variants = variants.flatMap { v -> if (unary) listOf("$v", "$v-") else listOf("$v+", "$v-") }
            i++
        }
        return variants
    }

    /** Evaluates a plain numeric expression (used by the Tools screens for inputs such as "3/2" or "sqrt(2)"). */
    fun evalValue(input: String, ws: Workspace, settings: CalcSettings): Value {
        val text = ExprUtil.balanceParens(input.trim())
        if (text.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Enter a number")
        val e = Parser(ws).parse(text)
        if (e is Expr.Rel || e is Expr.Chain || e is Expr.Assign || e is Expr.FuncDefStmt || (e is Expr.Call && e.name in Names.commands))
            throw MathError(ErrorKind.SYNTAX, "Enter a number or a numeric expression")
        val free = freeUnbound(e, ws)
        if (free.isNotEmpty()) throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${free.first()}'")
        return Evaluator(ws, settings.angle, EvalLimits.withTimeoutMs(settings.timeoutMs)).eval(e)
    }

    private fun rejectNestedCommands(st: Expr) {
        val inner: List<Expr> = if (st is Expr.Call && st.name in Names.commands) st.args else listOf(st)
        for (e in inner) {
            val bad = ExprUtil.calledFunctions(e).firstOrNull { it in Names.commands }
            if (bad != null) throw MathError(ErrorKind.UNSUPPORTED, "'$bad' must be used as a whole command, not inside an expression")
        }
    }

    private fun freeUnbound(e: Expr, ws: Workspace) = ExprUtil.freeVars(e).filter { ws.variable(it) == null }

    private fun plain(e: Expr, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        val free = freeUnbound(e, ws)
        if (free.isEmpty()) {
            val v = Evaluator(ws, s.angle, limits).eval(e)
            ws.ans = v
            return Outcome(OutcomeKind.VALUE, listOf(Line.Val(v)), value = v)
        }
        try {
            return solver.expand(e)
        } catch (ex: MathError) {
            if (ex.kind == ErrorKind.UNSUPPORTED)
                throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${free.first()}'. Assign it with ${free.first()} := value, or use the Graph tool.")
            throw ex
        }
    }

    private fun chain(ch: Expr.Chain, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        if (ch.terms.all { freeUnbound(it, ws).isEmpty() }) {
            val ev = Evaluator(ws, s.angle, limits)
            val vals = ch.terms.map { ev.eval(it) }
            var truth = true
            for (i in ch.ops.indices) {
                val a = vals[i]; val b = vals[i + 1]
                val c = if (a is Value.Q && b is Value.Q) a.r.compareTo(b.r) else {
                    val x = Ops.toDouble(a); val y = Ops.toDouble(b)
                    if (Math.abs(x - y) <= 1e-12 * maxOf(1.0, Math.abs(x), Math.abs(y))) 0 else x.compareTo(y)
                }
                val ok = when (ch.ops[i]) {
                    RelOp.EQ -> c == 0; RelOp.NE -> c != 0; RelOp.LT -> c < 0; RelOp.GT -> c > 0; RelOp.LE -> c <= 0; RelOp.GE -> c >= 0
                }
                if (!ok) truth = false
            }
            return Outcome(OutcomeKind.BOOLEAN, listOf(Line.Text(if (truth) "True" else "False")))
        }
        return solver.solveChain(ch.terms, ch.ops, ch.labels)
    }

    private fun relation(rel: Expr.Rel, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        if (freeUnbound(rel, ws).isEmpty()) {
            val ev = Evaluator(ws, s.angle, limits)
            val a = ev.eval(rel.l)
            val b = ev.eval(rel.r)
            val c = if (a is Value.Q && b is Value.Q) a.r.compareTo(b.r) else {
                val x = Ops.toDouble(a); val y = Ops.toDouble(b)
                if (Math.abs(x - y) <= 1e-12 * maxOf(1.0, Math.abs(x), Math.abs(y))) 0 else x.compareTo(y)
            }
            val truth = when (rel.op) {
                RelOp.EQ -> c == 0; RelOp.NE -> c != 0; RelOp.LT -> c < 0; RelOp.GT -> c > 0; RelOp.LE -> c <= 0; RelOp.GE -> c >= 0
            }
            return Outcome(OutcomeKind.BOOLEAN, listOf(Line.Text(if (truth) "True" else "False")))
        }
        return if (rel.op == RelOp.EQ) solver.solveEquation(rel.l, rel.r) else solver.solveInequality(rel.op, rel.l, rel.r)
    }

    private fun command(c: Expr.Call, text: String, ws: Workspace, s: CalcSettings, solver: Solver, limits: EvalLimits): Outcome {
        fun need(n: Int) { if (c.args.size != n) throw MathError(ErrorKind.SYNTAX, "${c.name}() expects $n argument(s)") }
        return when (c.name) {
            "solve" -> {
                if (c.args.isEmpty()) throw MathError(ErrorKind.SYNTAX, "solve() needs an equation")
                var args = c.args
                var prefer: String? = null
                if (args.size >= 2 && args.last() is Expr.Var && args.dropLast(1).all { it is Expr.Rel }) {
                    prefer = (args.last() as Expr.Var).name
                    args = args.dropLast(1)
                }
                if (args.size >= 2) {
                    if (args.any { it !is Expr.Rel }) throw MathError(ErrorKind.SYNTAX, "A system needs equations such as x + y = 3")
                    solver.solveSystem(args.map { it as Expr.Rel })
                } else when (val a = args[0]) {
                    is Expr.Rel -> if (a.op == RelOp.EQ) solver.solveEquation(a.l, a.r, prefer) else solver.solveInequality(a.op, a.l, a.r, prefer)
                    is Expr.Chain -> solver.solveChain(a.terms, a.ops, a.labels, prefer)
                    else -> solver.solveEquation(a, Expr.Num(Rat.ZERO), prefer)
                }
            }
            "simplify" -> { need(1); solver.simplify(c.args[0]) }
            "expand" -> { need(1); solver.expand(c.args[0]) }
            "factor" -> { need(1); solver.factor(c.args[0]) }
            "quad" -> {
                need(3)
                val ev = Evaluator(ws, s.angle, limits)
                val v = c.args.map { ev.eval(it) }
                if (v.any { it !is Value.Q }) throw MathError(ErrorKind.UNSUPPORTED, "quad(a, b, c) needs rational coefficients")
                solver.quadraticTool((v[0] as Value.Q).r, (v[1] as Value.Q).r, (v[2] as Value.Q).r)
            }
            "eval" -> {
                if (c.args.isEmpty()) throw MathError(ErrorKind.SYNTAX, "eval(expression; x = value, ...)")
                val ev = Evaluator(ws, s.angle, limits)
                val binds = LinkedHashMap<String, Value>()
                for (a in c.args.drop(1)) {
                    if (a !is Expr.Rel || a.op != RelOp.EQ || a.l !is Expr.Var) throw MathError(ErrorKind.SYNTAX, "Conditions must look like x = 2")
                    binds[a.l.name] = ev.eval(a.r)
                }
                val e = c.args[0]
                val remaining = ExprUtil.freeVars(e).filter { it !in binds && ws.variable(it) == null }
                if (remaining.isEmpty()) {
                    val v = Evaluator(ws, s.angle, limits, binds).eval(e)
                    ws.ans = v
                    Outcome(OutcomeKind.VALUE, listOf(Line.Val(v)), value = v)
                } else {
                    val layered = LayeredScope(ws, binds)
                    Solver(layered, s.angle).expand(e)
                }
            }
            "seq" -> {
                val ev = Evaluator(ws, s.angle, limits)
                val terms = c.args.map { a ->
                    val x = ev.eval(a)
                    (x as? Value.Q)?.r ?: throw MathError(ErrorKind.UNSUPPORTED, "seq() needs rational terms such as 2, 6, 18, 54")
                }
                val an = Sequences.analyze(terms)
                Outcome(OutcomeKind.ALGEBRA, an.lines.map { Line.Text(it) }, an.steps)
            }
            "plot" -> {
                val inner = text.trim().removePrefix("plot(").removeSuffix(")")
                Outcome(OutcomeKind.ALGEBRA, listOf(Line.Text("Plotting: $inner")), graphExpr = inner)
            }
            in Commands.names -> Commands.run(c, ws, s, limits)
            else -> throw MathError(ErrorKind.UNDEFINED, "Unknown command '${c.name}'")
        }
    }

    /** Lightweight live preview: only side-effect-free numeric expressions. */
    fun preview(input: String, ws: Workspace, settings: CalcSettings): String? {
        return try {
            val text = ExprUtil.balanceParens(input.trim())
            if (text.isEmpty()) return null
            val stmts = Parser(ws).parseAll(text)
            if (stmts.size != 1) return null
            val st = stmts[0]
            if (st is Expr.Rel || st is Expr.Chain || st is Expr.Assign || st is Expr.FuncDefStmt) return null
            if (st is Expr.Call && st.name in Names.commands) return null
            if (freeUnbound(st, ws).isNotEmpty()) return null
            val v = Evaluator(ws, settings.angle, EvalLimits.withTimeoutMs(500)).eval(st)
            Formatter.format(v, settings.format)
        } catch (e: MathError) { null }
    }
}
