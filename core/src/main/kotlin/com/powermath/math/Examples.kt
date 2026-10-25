package com.powermath.math

/** Ready-made inputs shown as one-tap chips on the Home page. Every example is checked by the test suite. */
data class Example(val category: String, val title: String, val input: String)

object Examples {
    val all: List<Example> = listOf(
        Example("Basics", "Fractions", "1/2 + 1/3"),
        Example("Basics", "Exact roots", "sqrt(50) + sqrt(2)"),
        Example("Basics", "Percent", "200 + 15%"),
        Example("Basics", "Plus or minus", "5 ± 2"),
        Example("Basics", "Trig exact", "sin(30)"),
        Example("Algebra", "Linear equation", "2x + 3 = 11"),
        Example("Algebra", "Quadratic", "x^2 - 5x + 6 = 0"),
        Example("Algebra", "Complex roots", "x^2 + x + 1 = 0"),
        Example("Algebra", "System", "x + y = 5; x - y = 1"),
        Example("Algebra", "Expand", "expand((x+2)^3)"),
        Example("Algebra", "Factor", "factor(x^2 - 9)"),
        Example("Inequalities", "Inequality", "x^2 - 4 < 0"),
        Example("Inequalities", "Chained", "4 < 2 + x < 6"),
        Example("Inequalities", "Closed chain", "-1 <= 3x + 2 <= 11"),
        Example("Inequalities", "Two intervals", "1 < x^2 < 4"),
        Example("Functions", "Make a function", "2x + 3"),
        Example("Functions", "Power", "x^1.1"),
        Example("Calculus", "Derivative", "diff(x^3 + 2x, x)"),
        Example("Calculus", "Derivative at a point", "diff(x^2, x, 3)"),
        Example("Calculus", "Integral", "integrate(3x^2 + 2x, x)"),
        Example("Calculus", "Area under a curve", "integrate(x^2, x, 0, 3)"),
        Example("Patterns", "Sequence pattern", "seq(2, 6, 18, 54)"),
        Example("Patterns", "Geometric term", "geom(3, 2, 10)"),
        Example("Patterns", "Infinite series", "geominf(1, 1/2)"),
        Example("Numbers", "Prime factors", "pfactor(360)"),
        Example("Numbers", "Is it prime?", "isprime(97)"),
        Example("Numbers", "Divisors", "divisors(28)"),
        Example("Numbers", "Binary", "bin(10)"),
        Example("Numbers", "Choose", "ncr(10, 3)")
    )

    val categories: List<String> get() = all.map { it.category }.distinct()
    fun of(category: String) = all.filter { it.category == category }
}
