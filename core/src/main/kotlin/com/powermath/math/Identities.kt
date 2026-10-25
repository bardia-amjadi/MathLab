package com.powermath.math

/** A named algebraic identity. [lhs]/[rhs] are parseable (explicit `*`), [display] is for reading. */
data class Identity(val category: String, val name: String, val display: String, val lhs: String, val rhs: String, val example: String)

object Identities {
    val all: List<Identity> = listOf(
        Identity("Squares", "Square of a sum", "(a + b)² = a² + 2ab + b²", "(a+b)^2", "a^2+2*a*b+b^2", "expand((x+3)^2)"),
        Identity("Squares", "Square of a difference", "(a − b)² = a² − 2ab + b²", "(a-b)^2", "a^2-2*a*b+b^2", "expand((x-5)^2)"),
        Identity("Squares", "Difference of squares", "a² − b² = (a + b)(a − b)", "a^2-b^2", "(a+b)*(a-b)", "factor(x^2-49)"),
        Identity("Squares", "Sum of squares from a sum", "a² + b² = (a + b)² − 2ab", "a^2+b^2", "(a+b)^2-2*a*b", "expand((x+2)^2-2*x*2)"),
        Identity("Squares", "Sum and difference squared", "(a + b)² + (a − b)² = 2(a² + b²)", "(a+b)^2+(a-b)^2", "2*(a^2+b^2)", "expand((x+1)^2+(x-1)^2)"),
        Identity("Squares", "Difference of squared sums", "(a + b)² − (a − b)² = 4ab", "(a+b)^2-(a-b)^2", "4*a*b", "expand((x+1)^2-(x-1)^2)"),
        Identity("Squares", "Square of a trinomial", "(a + b + c)² = a² + b² + c² + 2ab + 2bc + 2ca",
            "(a+b+c)^2", "a^2+b^2+c^2+2*a*b+2*b*c+2*c*a", "expand((x+y+1)^2)"),
        Identity("Cubes", "Cube of a sum", "(a + b)³ = a³ + 3a²b + 3ab² + b³", "(a+b)^3", "a^3+3*a^2*b+3*a*b^2+b^3", "expand((x+2)^3)"),
        Identity("Cubes", "Cube of a difference", "(a − b)³ = a³ − 3a²b + 3ab² − b³", "(a-b)^3", "a^3-3*a^2*b+3*a*b^2-b^3", "expand((x-1)^3)"),
        Identity("Cubes", "Sum of cubes", "a³ + b³ = (a + b)(a² − ab + b²)", "a^3+b^3", "(a+b)*(a^2-a*b+b^2)", "factor(x^3+8)"),
        Identity("Cubes", "Difference of cubes", "a³ − b³ = (a − b)(a² + ab + b²)", "a^3-b^3", "(a-b)*(a^2+a*b+b^2)", "factor(x^3-27)"),
        Identity("Higher", "Fourth power of a sum", "(a + b)⁴ = a⁴ + 4a³b + 6a²b² + 4ab³ + b⁴", "(a+b)^4", "a^4+4*a^3*b+6*a^2*b^2+4*a*b^3+b^4", "expand((x+1)^4)"),
        Identity("Higher", "Difference of fourth powers", "a⁴ − b⁴ = (a − b)(a + b)(a² + b²)", "a^4-b^4", "(a-b)*(a+b)*(a^2+b^2)", "factor(x^4-16)"),
        Identity("Products", "Product of two binomials", "(x + a)(x + b) = x² + (a + b)x + ab", "(x+a)*(x+b)", "x^2+(a+b)*x+a*b", "expand((x+2)*(x+5))"),
        Identity("Products", "General binomial product", "(a + b)(c + d) = ac + ad + bc + bd", "(a+b)*(c+d)", "a*c+a*d+b*c+b*d", "expand((x+1)*(y+2))")
    )

    /** True when lhs and rhs expand to the same polynomial (checked with the engine itself). */
    fun verify(id: Identity): Boolean {
        val parser = Parser(EmptyScope)
        val b = PolyBuilder(EmptyScope)
        val diff = b.build(parser.parse(id.lhs)) - b.build(parser.parse(id.rhs))
        return diff.terms.isEmpty()
    }

    /** Name of the special-product identity an expression is an instance of, or null. */
    fun recognize(e: Expr): String? {
        fun isNum(x: Expr, v: Int) = x is Expr.Num && x.value == Rat.of(v)
        if (e is Expr.Bin && e.op == '^' && e.l is Expr.Bin && (e.l.op == '+' || e.l.op == '-')) {
            val plus = e.l.op == '+'
            return when {
                isNum(e.r, 2) -> if (plus) "Square of a sum: (a + b)² = a² + 2ab + b²" else "Square of a difference: (a − b)² = a² − 2ab + b²"
                isNum(e.r, 3) -> if (plus) "Cube of a sum: (a + b)³ = a³ + 3a²b + 3ab² + b³" else "Cube of a difference: (a − b)³ = a³ − 3a²b + 3ab² − b³"
                isNum(e.r, 4) && plus -> "Fourth power of a sum: (a + b)⁴ = a⁴ + 4a³b + 6a²b² + 4ab³ + b⁴"
                else -> null
            }
        }
        if (e is Expr.Bin && e.op == '*' && e.l is Expr.Bin && e.r is Expr.Bin) {
            val l = e.l; val r = e.r
            if ((l.op == '+' && r.op == '-' || l.op == '-' && r.op == '+') &&
                ((l.l == r.l && l.r == r.r) || (l.l == r.r && l.r == r.l && l.op == '+')))
                return "Difference of squares: (a + b)(a − b) = a² − b²"
            if (l.op == '+' && r.op == '+' && l.l == r.l && l.l is Expr.Var)
                return "Product of binomials: (x + a)(x + b) = x² + (a + b)x + ab"
        }
        return null
    }
}
