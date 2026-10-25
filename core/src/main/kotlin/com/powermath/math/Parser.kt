package com.powermath.math

/**
 * Pratt parser.
 * Precedence (binding power): relation < + - (10) < * / and implicit multiplication (20)
 *  < unary minus operand (21) < ^ (30, right assoc) < postfix ! % (40).
 * Implicit multiplication has the same precedence as explicit '*': 1/2x == (1/2)*x, 2x^2 == 2*(x^2).
 */
class Parser(private val scope: Scope = EmptyScope) {
    private var toks: List<Token> = emptyList()
    private var p = 0
    private var src = ""
    private var depth = 0

    private val peek: Token get() = toks[p]
    private fun next(): Token = toks[p++]

    fun parse(source: String): Expr {
        val all = parseAll(source)
        if (all.size > 1) throw MathError(ErrorKind.SYNTAX, "Multiple statements are only allowed for systems of equations")
        return all[0]
    }

    fun parseAll(source: String): List<Expr> {
        src = source; toks = Lexer.tokenize(source); p = 0; depth = 0
        if (toks[0].type == T.EOF) throw MathError(ErrorKind.SYNTAX, "Empty expression")
        val out = ArrayList<Expr>()
        while (true) {
            out.add(parseStatement())
            if (peek.type == T.SEMI) { p++; if (peek.type == T.EOF) break else continue }
            break
        }
        if (peek.type != T.EOF) {
            if (peek.type == T.RP) throw MathError(ErrorKind.SYNTAX, "Unmatched closing parenthesis", peek.pos)
            throw MathError(ErrorKind.SYNTAX, "Unexpected '${peek.text}'", peek.pos)
        }
        return out
    }

    private fun parseStatement(): Expr {
        tryFuncDef()?.let { return it }
        if (peek.type == T.IDENT && toks[p + 1].type == T.OP && toks[p + 1].text == ":=") {
            val name = next().text
            p++
            val start = peek.pos
            val e = parseExpr(0)
            return Expr.Assign(name, e, src.substring(start, peek.pos).trim())
        }
        return parseRelationOrExpr()
    }

    private fun tryFuncDef(): Expr? {
        if (peek.type != T.IDENT || toks[p + 1].type != T.LP) return null
        val name = peek.text
        if (name in Names.functions || name in Names.commands || scope.isVariable(name)) return null
        var q = p + 2
        val params = ArrayList<String>()
        if (toks[q].type == T.RP) return null
        while (true) {
            if (toks[q].type != T.IDENT) return null
            params.add(toks[q].text); q++
            if (toks[q].type == T.COMMA) { q++; continue }
            if (toks[q].type == T.RP) { q++; break }
            return null
        }
        val opTok = toks[q]
        if (opTok.type != T.OP || (opTok.text != "=" && opTok.text != ":=")) return null
        // "f(x) = 5" with an existing function f is an equation to solve; ":=" redefines explicitly.
        if (opTok.text == "=" && scope.isFunction(name)) return null
        p = q + 1
        if (peek.type == T.EOF || peek.type == T.SEMI) throw MathError(ErrorKind.SYNTAX, "Function body is missing", peek.pos)
        val start = peek.pos
        val body = parseExpr(0)
        return Expr.FuncDefStmt(name, params, body, src.substring(start, peek.pos).trim(), opTok.text == ":=")
    }

    private fun relOpOf(t: Token): RelOp? = if (t.type != T.OP) null else when (t.text) {
        "=" -> RelOp.EQ; "<" -> RelOp.LT; ">" -> RelOp.GT; "<=" -> RelOp.LE; ">=" -> RelOp.GE; "!=" -> RelOp.NE
        else -> null
    }

    private fun parseRelationOrExpr(): Expr {
        val s0 = peek.pos
        val left = parseExpr(0)
        val e0 = peek.pos
        val op = relOpOf(peek) ?: return left
        p++
        val s1 = peek.pos
        val right = parseExpr(0)
        val e1 = peek.pos
        if (relOpOf(peek) == null) return Expr.Rel(op, left, right)
        val terms = arrayListOf(left, right)
        val labels = arrayListOf(src.substring(s0, e0).trim(), src.substring(s1, e1).trim())
        val ops = arrayListOf(op)
        while (relOpOf(peek) != null) {
            ops.add(relOpOf(peek)!!)
            p++
            val s = peek.pos
            terms.add(parseExpr(0))
            labels.add(src.substring(s, peek.pos).trim())
        }
        if (terms.size > 6) throw MathError(ErrorKind.LIMIT, "At most five comparison signs can be chained")
        return Expr.Chain(terms, ops, labels)
    }

    private fun startsOperand(t: Token) = t.type == T.NUM || t.type == T.IDENT || t.type == T.LP

    private fun parseExpr(minBp: Int): Expr {
        if (++depth > 200) throw MathError(ErrorKind.LIMIT, "Expression is nested too deeply")
        try {
            var left = parsePrefix()
            while (true) {
                val t = peek
                if (t.type == T.OP) {
                    when (t.text) {
                        "!" -> { if (40 < minBp) break; p++; left = Expr.Fact(left); continue }
                        "%" -> { if (40 < minBp) break; p++; left = Expr.Percent(left); continue }
                        "^" -> { if (30 < minBp) break; p++; left = Expr.Bin('^', left, parseExpr(30)); continue }
                        "+", "-" -> { if (10 < minBp) break; p++; left = Expr.Bin(t.text[0], left, parseExpr(11)); continue }
                        "*", "/" -> { if (20 < minBp) break; p++; left = Expr.Bin(t.text[0], left, parseExpr(21)); continue }
                        else -> break
                    }
                }
                if (startsOperand(t)) {
                    if (20 < minBp) break
                    if (left is Expr.Num && t.type == T.NUM) throw MathError(ErrorKind.SYNTAX, "Unexpected number '${t.text}'", t.pos)
                    left = Expr.Bin('*', left, parseExpr(21))
                    continue
                }
                break
            }
            return left
        } finally { depth-- }
    }

    private fun parsePrefix(): Expr {
        val t = next()
        when (t.type) {
            T.NUM -> return Expr.Num(t.num!!)
            T.OP -> when (t.text) {
                "-" -> return Expr.Neg(parseExpr(21))
                "+" -> return parseExpr(21)
                else -> throw MathError(ErrorKind.SYNTAX, "Unexpected '${t.text}'", t.pos)
            }
            T.LP -> {
                val e = parseExpr(0)
                expectRp()
                return e
            }
            T.IDENT -> return parseIdent(t)
            T.EOF -> throw MathError(ErrorKind.SYNTAX, "Unexpected end of expression", t.pos)
            T.RP -> throw MathError(ErrorKind.SYNTAX, "Unexpected ')'", t.pos)
            else -> throw MathError(ErrorKind.SYNTAX, "Unexpected '${t.text}'", t.pos)
        }
    }

    private fun expectRp() {
        if (peek.type == T.RP) { p++; return }
        if (peek.type == T.EOF) throw MathError(ErrorKind.SYNTAX, "Missing closing parenthesis", peek.pos)
        throw MathError(ErrorKind.SYNTAX, "Expected ')' but found '${peek.text}'", peek.pos)
    }

    private fun isFunctionName(name: String) = name in Names.functions || name in Names.commands || scope.isFunction(name)

    private fun atom(name: String): Expr = if (name in Names.constants) Expr.Const(name) else Expr.Var(name)

    private fun parseIdent(t: Token): Expr {
        val name = t.text
        val nextIsLp = peek.type == T.LP
        if (t.radical && !nextIsLp) {
            return Expr.Call("sqrt", listOf(parseExpr(40)))
        }
        if (nextIsLp) {
            val known = isFunctionName(name)
            val callable = known || (name.length > 1 && !scope.isVariable(name) && name !in Names.constants)
            if (callable) return parseCall(name)
            return atom(name)
        }
        if (name in Names.constants || scope.isVariable(name) || name == "ans") return atom(name)
        if (isFunctionName(name)) throw MathError(ErrorKind.SYNTAX, "Function '$name' needs parentheses, e.g. $name(x)", t.pos)
        if (name.length == 1 || name.contains('_')) return atom(name)
        val fnPrefix = Names.functions.keys.firstOrNull { it.length >= 2 && name.startsWith(it) }
        if (fnPrefix != null) throw MathError(ErrorKind.SYNTAX, "Function '$fnPrefix' needs parentheses, e.g. $fnPrefix(x)", t.pos)
        // split e.g. "xy" into x*y, honoring known multi-letter names
        var i = 0
        var result: Expr? = null
        while (i < name.length) {
            var matched: String? = null
            for (len in (name.length - i) downTo 2) {
                val cand = name.substring(i, i + len)
                if (cand in Names.constants || scope.isVariable(cand)) { matched = cand; break }
            }
            val part = matched ?: name[i].toString()
            i += part.length
            val a = atom(part)
            result = if (result == null) a else Expr.Bin('*', result, a)
        }
        return result!!
    }

    private fun parseCall(name: String): Expr {
        p++ // '('
        val args = ArrayList<Expr>()
        if (peek.type == T.RP) { p++; return Expr.Call(name, args) }
        while (true) {
            args.add(parseRelationOrExpr())
            if (peek.type == T.COMMA || peek.type == T.SEMI) { p++; continue }
            break
        }
        expectRp()
        return Expr.Call(name, args)
    }
}

object ExprUtil {
    /** Free variable names in order of first appearance (excluding constants). Function bodies are not entered. */
    fun freeVars(e: Expr, out: LinkedHashSet<String> = LinkedHashSet()): LinkedHashSet<String> {
        when (e) {
            is Expr.Var -> out.add(e.name)
            is Expr.Neg -> freeVars(e.e, out)
            is Expr.Bin -> { freeVars(e.l, out); freeVars(e.r, out) }
            is Expr.Call -> e.args.forEach { freeVars(it, out) }
            is Expr.Percent -> freeVars(e.e, out)
            is Expr.Fact -> freeVars(e.e, out)
            is Expr.Rel -> { freeVars(e.l, out); freeVars(e.r, out) }
            is Expr.Chain -> e.terms.forEach { freeVars(it, out) }
            is Expr.Assign -> freeVars(e.e, out)
            is Expr.FuncDefStmt -> freeVars(e.body, out)
            else -> {}
        }
        return out
    }

    fun calledFunctions(e: Expr, out: LinkedHashSet<String> = LinkedHashSet()): LinkedHashSet<String> {
        when (e) {
            is Expr.Call -> { out.add(e.name); e.args.forEach { calledFunctions(it, out) } }
            is Expr.Neg -> calledFunctions(e.e, out)
            is Expr.Bin -> { calledFunctions(e.l, out); calledFunctions(e.r, out) }
            is Expr.Percent -> calledFunctions(e.e, out)
            is Expr.Fact -> calledFunctions(e.e, out)
            is Expr.Rel -> { calledFunctions(e.l, out); calledFunctions(e.r, out) }
            is Expr.Chain -> e.terms.forEach { calledFunctions(it, out) }
            else -> {}
        }
        return out
    }

    /** Auto-close missing ')' at the end; extra ')' is left for the parser to report. */
    fun balanceParens(src: String): String {
        var open = 0
        for (c in src) { if (c == '(') open++ else if (c == ')' && open > 0) open-- }
        return if (open > 0) src + ")".repeat(open) else src
    }
}
