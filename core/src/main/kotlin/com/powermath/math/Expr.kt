package com.powermath.math

enum class RelOp(val symbol: String) { EQ("="), LT("<"), GT(">"), LE("≤"), GE("≥"), NE("≠") }
enum class AngleMode { DEG, RAD }

/** The single internal representation used by calculator, solver, simplifier and graph. */
sealed class Expr {
    data class Num(val value: Rat) : Expr()
    data class Const(val name: String) : Expr()
    data class Var(val name: String) : Expr()
    data class Neg(val e: Expr) : Expr()
    /** op is one of + - * / ^ */
    data class Bin(val op: Char, val l: Expr, val r: Expr) : Expr()
    data class Call(val name: String, val args: List<Expr>) : Expr()
    data class Percent(val e: Expr) : Expr()
    data class Fact(val e: Expr) : Expr()
    data class Rel(val op: RelOp, val l: Expr, val r: Expr) : Expr()
    /** a < b ≤ c ... : terms.size == ops.size + 1 */
    data class Chain(val terms: List<Expr>, val ops: List<RelOp>, val labels: List<String>) : Expr()
    data class FuncDefStmt(val name: String, val params: List<String>, val body: Expr, val bodySource: String, val overwrite: Boolean) : Expr()
    data class Assign(val name: String, val e: Expr, val source: String) : Expr()
}

data class FuncDef(val name: String, val params: List<String>, val source: String, val body: Expr)

interface Scope {
    fun variable(name: String): Value?
    fun function(name: String): FuncDef?
    fun isVariable(name: String): Boolean = variable(name) != null
    fun isFunction(name: String): Boolean = function(name) != null
}

object EmptyScope : Scope {
    override fun variable(name: String): Value? = null
    override fun function(name: String): FuncDef? = null
}

/** Scope layering: extra bindings on top of a base scope. */
class LayeredScope(private val base: Scope, private val vars: Map<String, Value>) : Scope {
    override fun variable(name: String): Value? = vars[name] ?: base.variable(name)
    override fun function(name: String): FuncDef? = base.function(name)
}

object Names {
    val constants = setOf("pi", "e", "phi", "tau")
    val commands: Set<String> = setOf("solve", "simplify", "expand", "factor", "eval", "quad", "plot", "seq") + Commands.names
    val functions: Map<String, IntRange> = mapOf(
        "sqrt" to 1..1, "root" to 2..2, "abs" to 1..1,
        "sin" to 1..1, "cos" to 1..1, "tan" to 1..1,
        "asin" to 1..1, "acos" to 1..1, "atan" to 1..1,
        "sinh" to 1..1, "cosh" to 1..1, "tanh" to 1..1,
        "ln" to 1..1, "log" to 1..2, "exp" to 1..1,
        "mean" to 1..64, "min" to 1..64, "max" to 1..64,
        "floor" to 1..1, "ceil" to 1..1, "sgn" to 1..1,
        "fact" to 1..1, "gcd" to 2..2, "lcm" to 2..2, "mod" to 2..2,
        "ncr" to 2..2, "npr" to 2..2, "delta" to 3..3,
        "arith" to 3..3, "arithsum" to 3..3, "geom" to 3..3, "geomsum" to 3..3, "geominf" to 2..2
    )
    val reserved: Set<String> = constants + commands + functions.keys + setOf("ans")

    private val nameRegex = Regex("^[A-Za-z][A-Za-z_]*$")
    fun isValidName(n: String) = nameRegex.matches(n)
}

enum class T { NUM, IDENT, OP, LP, RP, COMMA, SEMI, EOF }

data class Token(val type: T, val text: String, val pos: Int, val num: Rat? = null, val radical: Boolean = false)

object Lexer {
    fun tokenize(src: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit()) -> {
                    val start = i
                    var intPart = ""
                    var frac = ""
                    while (i < n && src[i].isDigit()) { intPart += src[i]; i++ }
                    if (i < n && src[i] == '.') {
                        i++
                        while (i < n && src[i].isDigit()) { frac += src[i]; i++ }
                        if (i < n && src[i] == '.') throw MathError(ErrorKind.SYNTAX, "Invalid number: too many decimal points", i)
                    }
                    var exp = 0
                    if (i < n && src[i] == 'E') {
                        var j = i + 1
                        var sign = 1
                        if (j < n && (src[j] == '+' || src[j] == '-' || src[j] == '−')) { if (src[j] != '+') sign = -1; j++ }
                        if (j < n && src[j].isDigit()) {
                            var ds = ""
                            while (j < n && src[j].isDigit()) { ds += src[j]; j++ }
                            if (ds.length > 6) throw MathError(ErrorKind.LIMIT, "Exponent is too large", i)
                            exp = sign * ds.toInt()
                            i = j
                        }
                    }
                    out.add(Token(T.NUM, src.substring(start, i), start, Rat.fromDecimal(intPart, frac, exp)))
                }
                (c in 'a'..'z') || (c in 'A'..'Z') || c == '_' -> {
                    val start = i
                    while (i < n && ((src[i] in 'a'..'z') || (src[i] in 'A'..'Z') || src[i] == '_')) i++
                    val word = src.substring(start, i)
                    out.add(Token(T.IDENT, word, start))
                }
                c == 'π' -> { out.add(Token(T.IDENT, "pi", i)); i++ }
                c == '√' -> { out.add(Token(T.IDENT, "sqrt", i, radical = true)); i++ }
                c == '²' -> { out.add(Token(T.OP, "^", i)); out.add(Token(T.NUM, "2", i, Rat.of(2))); i++ }
                c == '³' -> { out.add(Token(T.OP, "^", i)); out.add(Token(T.NUM, "3", i, Rat.of(3))); i++ }
                c == '(' -> { out.add(Token(T.LP, "(", i)); i++ }
                c == ')' -> { out.add(Token(T.RP, ")", i)); i++ }
                c == ',' -> { out.add(Token(T.COMMA, ",", i)); i++ }
                c == ';' -> { out.add(Token(T.SEMI, ";", i)); i++ }
                c == '+' -> { out.add(Token(T.OP, "+", i)); i++ }
                c == '-' || c == '−' || c == '–' -> { out.add(Token(T.OP, "-", i)); i++ }
                c == '*' || c == '×' || c == '·' || c == '⋅' -> { out.add(Token(T.OP, "*", i)); i++ }
                c == '/' || c == '÷' -> { out.add(Token(T.OP, "/", i)); i++ }
                c == '^' -> { out.add(Token(T.OP, "^", i)); i++ }
                c == '%' -> { out.add(Token(T.OP, "%", i)); i++ }
                c == '±' -> { out.add(Token(T.OP, "±", i)); i++ }
                c == '!' -> {
                    if (i + 1 < n && src[i + 1] == '=') { out.add(Token(T.OP, "!=", i)); i += 2 }
                    else { out.add(Token(T.OP, "!", i)); i++ }
                }
                c == '≠' -> { out.add(Token(T.OP, "!=", i)); i++ }
                c == '≤' -> { out.add(Token(T.OP, "<=", i)); i++ }
                c == '≥' -> { out.add(Token(T.OP, ">=", i)); i++ }
                c == '<' -> { if (i + 1 < n && src[i + 1] == '=') { out.add(Token(T.OP, "<=", i)); i += 2 } else { out.add(Token(T.OP, "<", i)); i++ } }
                c == '>' -> { if (i + 1 < n && src[i + 1] == '=') { out.add(Token(T.OP, ">=", i)); i += 2 } else { out.add(Token(T.OP, ">", i)); i++ } }
                c == ':' -> { if (i + 1 < n && src[i + 1] == '=') { out.add(Token(T.OP, ":=", i)); i += 2 } else throw MathError(ErrorKind.SYNTAX, "Unexpected ':' (did you mean ':=')", i) }
                c == '=' -> { if (i + 1 < n && src[i + 1] == '=') i += 2 else i++; out.add(Token(T.OP, "=", i)) }
                else -> throw MathError(ErrorKind.SYNTAX, "Unexpected character '$c'", i)
            }
        }
        out.add(Token(T.EOF, "", n))
        return out
    }
}
