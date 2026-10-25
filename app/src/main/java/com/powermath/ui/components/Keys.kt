package com.powermath.ui.components

import com.powermath.viewmodel.DialogRequest
import com.powermath.viewmodel.ModifierState
import com.powermath.viewmodel.PickPurpose
import com.powermath.viewmodel.Screen
import com.powermath.viewmodel.ToolKind

sealed interface KeyAction {
    data object None : KeyAction
    /** Cycles through the pages of the letter keyboard. */
    data object LetterPage : KeyAction
    data class Insert(val text: String) : KeyAction
    /** Wraps the current expression, e.g. prefix "simplify(", and evaluates it. */
    data class Wrap(val prefix: String) : KeyAction
    data object Backspace : KeyAction
    data object ClearEntry : KeyAction
    data object ClearAll : KeyAction
    data object Equals : KeyAction
    data object Left : KeyAction
    data object Right : KeyAction
    data object Negate : KeyAction
    /** Fraction template: the number before the cursor becomes the numerator. */
    data object Fraction : KeyAction
    data class Open(val dialog: DialogRequest) : KeyAction
    data class Go(val screen: Screen) : KeyAction
    data object CopyResult : KeyAction
    data object Paste : KeyAction
    data object SaveResultAsVar : KeyAction
    data object SaveExprAsFn : KeyAction
    data object ClearVars : KeyAction
    data object ClearFns : KeyAction
    data object ClearHistory : KeyAction
}

enum class KeyKind { NUMBER, OPERATOR, FUNCTION, ACTION, EQUALS, SPACER }

data class KeyDef(
    val label: String,
    val action: KeyAction,
    val kind: KeyKind = KeyKind.FUNCTION,
    val weight: Float = 1f,
    val description: String = label
)

/** Key layers selected by the Shift / Alt / Ctrl modifier combination. Every visible key has working behaviour. */
object KeyLayouts {
    private fun n(d: String) = KeyDef(d, KeyAction.Insert(d), KeyKind.NUMBER)
    private fun op(label: String, text: String, desc: String = label) = KeyDef(label, KeyAction.Insert(text), KeyKind.OPERATOR, description = desc)
    private fun ins(label: String, text: String = label, desc: String = label) = KeyDef(label, KeyAction.Insert(text), KeyKind.FUNCTION, description = desc)
    private fun act(label: String, action: KeyAction, desc: String = label) = KeyDef(label, action, KeyKind.ACTION, description = desc)
    private val spacer = KeyDef("", KeyAction.None, KeyKind.SPACER)
    private fun fn(label: String, call: String, desc: String = label) = ins(label, call, desc)

    private val bottom = listOf(
        act("◀", KeyAction.Left, "Move cursor left"),
        act("▶", KeyAction.Right, "Move cursor right"),
        act("⌫", KeyAction.Backspace, "Backspace"),
        act("AC", KeyAction.ClearAll, "Clear all"),
        KeyDef("=", KeyAction.Equals, KeyKind.EQUALS, description = "Equals")
    )

    private val standard = listOf(
        listOf(ins("("), ins(")"), op("%", "%", "Percent"), op("xʸ", "^", "Power"), fn("√", "sqrt(", "Square root")),
        listOf(n("7"), n("8"), n("9"), op("÷", "/", "Divide"), act("CE", KeyAction.ClearEntry, "Clear entry")),
        listOf(n("4"), n("5"), n("6"), op("×", "*", "Multiply"), ins("π", "pi", "Pi")),
        listOf(n("1"), n("2"), n("3"), op("−", "-", "Subtract"), KeyDef("a⁄b", KeyAction.Fraction, KeyKind.OPERATOR, description = "Fraction: the number before the cursor becomes the numerator")),
        listOf(n("0"), n("."), act("(−)", KeyAction.Negate, "Negative sign"), op("+", "+", "Add"), ins(",", ",", "Comma"))
    )

    private val alt = listOf(
        listOf(ins("x"), ins("y"), ins("z"), ins("("), ins(")")),
        listOf(ins("a"), ins("b"), ins("c"), ins("d"), ins("f")),
        listOf(ins("g"), ins(",", ",", "Comma"), ins("=", "=", "Equals sign"), ins("<"), ins(">")),
        listOf(ins("≤", "<=", "Less or equal"), ins("≥", ">=", "Greater or equal"), ins("≠", "!=", "Not equal"), ins(":=", ":=", "Assign"), ins(";", ";", "Separate equations")),
        listOf(ins("π", "pi"), ins("e"), op("%", "%"), ins("n!", "!", "Factorial"), op("xʸ", "^", "Power"))
    )

    private val shift = listOf(
        listOf(fn("√", "sqrt(", "Square root"), fn("ⁿ√", "root(", "Nth root"), ins("x²", "^2", "Square"), ins("xʸ", "^", "Power"), ins("n!", "!", "Factorial")),
        listOf(fn("sin", "sin("), fn("cos", "cos("), fn("tan", "tan("), fn("sin⁻¹", "asin(", "Inverse sine"), fn("cos⁻¹", "acos(", "Inverse cosine")),
        listOf(fn("tan⁻¹", "atan(", "Inverse tangent"), fn("ln", "ln(", "Natural log"), fn("log", "log(", "Log base 10"), fn("eˣ", "exp(", "Exponential"), fn("|x|", "abs(", "Absolute value")),
        listOf(fn("mean", "mean(", "Arithmetic mean"), fn("Δ", "delta(", "Quadratic discriminant"), fn("x₁,₂", "quad(", "Quadratic roots"), fn("min", "min("), fn("max", "max(")),
        listOf(fn("⌊x⌋", "floor(", "Floor"), fn("⌈x⌉", "ceil(", "Ceiling"), ins("10ˣ", "10^", "Ten to the power"), ins("1/x", "^(-1)", "Reciprocal"), ins("±", "±", "Plus or minus: gives both results"))
    )

    private val shiftAlt = listOf(
        listOf(ins("x²", "x^2"), ins("x³", "x^3"), ins("√x", "sqrt(x)"), ins("1/x", "(1/x)"), ins("|x|", "abs(x)")),
        listOf(ins("sin x", "sin(x)"), ins("cos x", "cos(x)"), ins("tan x", "tan(x)"), ins("ln x", "ln(x)"), ins("eˣ", "exp(x)")),
        listOf(ins("f(x)", "f(x)"), ins("g(x)", "g(x)"), ins("h(x,y)", "h(x,y)"), ins("xʸ", "x^y"), ins("x/y", "x/y")),
        listOf(ins("x+y", "x+y"), ins("x−y", "x-y"), ins("x·y", "x*y"), ins("(x+y)²", "(x+y)^2"), ins("x²+y²", "x^2+y^2")),
        listOf(ins("ax²+bx+c", "a*x^2+b*x+c"), ins("x=", "x="), ins("y=", "y="), ins("Δ", "delta(a,b,c)"), ins("x₁,₂", "quad(a,b,c)"))
    )

    private val ctrlAlt = listOf(
        listOf(
            act("Solve", KeyAction.Open(DialogRequest.Tool(ToolKind.EQUATION)), "Solve an equation"),
            act("Ineq", KeyAction.Open(DialogRequest.Tool(ToolKind.INEQUALITY)), "Solve an inequality"),
            act("Quad", KeyAction.Open(DialogRequest.Tool(ToolKind.QUADRATIC)), "Solve a quadratic equation"),
            act("System", KeyAction.Open(DialogRequest.Tool(ToolKind.SYSTEM)), "Solve a system of equations"),
            act("Eval@", KeyAction.Open(DialogRequest.Tool(ToolKind.EVALUATE)), "Evaluate under conditions")
        ),
        listOf(
            act("Simplify", KeyAction.Wrap("simplify("), "Simplify the expression"),
            act("Expand", KeyAction.Wrap("expand("), "Expand the expression"),
            act("Factor", KeyAction.Wrap("factor("), "Factor the polynomial"),
            act("Graph", KeyAction.Go(Screen.GRAPH), "Open graphing tools"),
            fn("solve(", "solve(", "Insert solve command")
        ),
        listOf(ins("=", "="), ins("<"), ins(">"), ins("≤", "<="), ins("≥", ">=")),
        listOf(ins("≠", "!="), ins("x"), ins("y"), ins(";", ";"), ins(",", ",")),
        listOf(ins("x²", "^2"), ins("(", "("), ins(")", ")"), ins("a"), ins("b"))
    )

    private val shiftCtrl = listOf(
        listOf(
            act("Save→var", KeyAction.SaveResultAsVar, "Save the result as a variable"),
            act("Save→fn", KeyAction.SaveExprAsFn, "Save the expression as a function"),
            ins("Ans", "ans", "Previous result"),
            act("Copy", KeyAction.CopyResult, "Copy the result"),
            act("Paste", KeyAction.Paste, "Paste from clipboard")
        ),
        listOf(
            act("Clr vars", KeyAction.ClearVars, "Delete all variables"),
            act("Clr fns", KeyAction.ClearFns, "Delete all functions"),
            act("Clr hist", KeyAction.ClearHistory, "Clear history"),
            act("Settings", KeyAction.Go(Screen.SETTINGS), "Open settings"),
            act("Graph", KeyAction.Go(Screen.GRAPH), "Open graph")
        ),
        List(5) { spacer }, List(5) { spacer }, List(5) { spacer }
    )

    private val extended = listOf(
        listOf(ins("φ", "phi", "Golden ratio"), ins("τ", "tau", "Tau (2 pi)"), fn("gcd", "gcd("), fn("lcm", "lcm("), fn("mod", "mod(")),
        listOf(fn("nCr", "ncr(", "Combinations"), fn("nPr", "npr(", "Permutations"), fn("sinh", "sinh("), fn("cosh", "cosh("), fn("tanh", "tanh(")),
        listOf(fn("logᵦ", "log(", "Log with base: log(b, x)"), fn("fact", "fact("), ins("E", "E", "Scientific exponent, e.g. 1.5E3"), ins("ans", "ans", "Previous result"), ins(":=", ":=", "Assign")),
        listOf(fn("d/dx", "diff(", "Derivative: diff(expression, x)"), fn("∫", "integrate(", "Integral: integrate(expression, x) or integrate(expression, x, a, b)"),
            fn("prime?", "isprime(", "Is the number prime?"), fn("factors", "pfactor(", "Prime factors"), fn("divisors", "divisors(", "All divisors")),
        listOf(fn("φ(n)", "totient(", "Euler's totient"), fn("next prime", "nextprime(", "Next prime number"), fn("bin", "bin(", "Binary form"), fn("hex", "hex(", "Hexadecimal form"), fn("tobase", "tobase(", "Convert to any base: tobase(n, b)"))
    )

    private fun page(rows: List<List<KeyDef>>, label: String): List<List<KeyDef>> =
        rows + listOf(listOf(act(label, KeyAction.LetterPage, "Next page of letters"), ins(",", ",", "Comma"), ins("=", "=", "Equals sign"), ins("(", "(", "Open bracket"), ins(")", ")", "Close bracket")))

    private fun letters(chars: String) = chars.map { ins(it.toString(), it.toString(), "Letter $it") }

    private fun greek(label: String, name: String) = ins(label, name, "Greek letter $name")

    /** The letter keyboard: a–t, then u–z with Greek letters, then symbols. A page key replaces the first key of the bottom row. */
    private val letterPages: List<List<List<KeyDef>>> = listOf(
        page(letters("abcdefghijklmnopqrst").chunked(5), "a–t ▸"),
        page((letters("uvwxyz") + listOf(greek("α", "alpha"), greek("β", "beta"), greek("γ", "gamma"), greek("θ", "theta"), greek("λ", "lambda"), greek("μ", "mu"),
            greek("ρ", "rho"), greek("σ", "sigma"), greek("ω", "omega"), greek("η", "eta"), greek("κ", "kappa"), greek("ν", "nu"), greek("ψ", "psi"), greek("χ", "chi"))).chunked(5), "u–z ▸"),
        page(listOf(
            listOf(ins(";", ";", "Separate equations"), ins("<"), ins(">"), ins("≤", "<=", "Less or equal"), ins("≥", ">=", "Greater or equal")),
            listOf(ins("≠", "!=", "Not equal"), ins(":=", ":=", "Assign"), ins("_", "_", "Underscore for names such as x_1"), ins("!", "!", "Factorial"), ins("%", "%", "Percent")),
            listOf(ins("^", "^", "Power"), ins("π", "pi", "Pi"), ins("e", "e", "Euler's number"), ins("φ", "phi", "Golden ratio"), ins("τ", "tau", "Tau (2 pi)")),
            listOf(ins("ans", "ans", "Previous result"), ins("±", "±", "Plus or minus"), ins("x²", "^2", "Square"), ins("E", "E", "Scientific exponent"), ins(".", ".", "Decimal point"))
        ), "symbols ▸")
    )

    fun layer(m: ModifierState, variables: List<String>, functions: List<String>): List<List<KeyDef>> {
        if (m.abc) return letterPages[m.letterPage % letterPages.size].let { it + listOf(bottom) }
        val rows: List<List<KeyDef>> = when {
            m.shift && m.alt && m.ctrl -> extended
            m.ctrl && m.alt -> ctrlAlt
            m.shift && m.ctrl -> shiftCtrl
            m.shift && m.alt -> shiftAlt
            m.shift -> shift
            m.alt -> alt
            m.ctrl -> ctrlRows(variables, functions)
            else -> standard
        }
        return rows + listOf(bottom)
    }

    private fun ctrlRows(variables: List<String>, functions: List<String>): List<List<KeyDef>> {
        val head = listOf(
            listOf(
                act("Def var", KeyAction.Open(DialogRequest.DefineVar()), "Define a variable"),
                act("Edit var", KeyAction.Open(DialogRequest.Pick(PickPurpose.EDIT_VAR)), "Edit a variable"),
                act("Del var", KeyAction.Open(DialogRequest.Pick(PickPurpose.DELETE_VAR)), "Delete a variable"),
                act("Def fn", KeyAction.Open(DialogRequest.DefineFn()), "Define a function"),
                act("Edit fn", KeyAction.Open(DialogRequest.Pick(PickPurpose.EDIT_FN)), "Edit a function")
            ),
            listOf(
                act("Del fn", KeyAction.Open(DialogRequest.Pick(PickPurpose.DELETE_FN)), "Delete a function"),
                act("Insert…", KeyAction.Open(DialogRequest.Pick(PickPurpose.INSERT)), "Insert a saved variable or function"),
                act("Workspace", KeyAction.Go(Screen.WORKSPACE), "View workspace"),
                act("History", KeyAction.Go(Screen.HISTORY), "View history"),
                act("Graph", KeyAction.Go(Screen.GRAPH), "Open graph")
            )
        )
        // Quick-insert keys for saved names (variables first, then functions).
        val saved = variables.map { ins(it, it, "Variable $it") } + functions.map { ins("$it()", "$it(", "Function $it") }
        val padded = saved.take(15) + List((15 - saved.take(15).size).coerceAtLeast(0)) { spacer }
        return head + padded.chunked(5)
    }
}
