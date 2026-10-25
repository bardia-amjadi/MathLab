# Power Math User Guide

## Result modes

- **Exact / Fraction**: `1/3 + 1/6` gives `1/2`; `sqrt(8)` gives `2√2`; `sin(60)` in DEG gives `√3/2`.
- **Decimal**: the same value as a decimal, with ≈ when rounded.
- **Scientific**: for example `1.23 × 10^6`.

The **DEG / RAD** chip switches the angle mode. It also applies to the graph.

## What you can type

Operators: `+ − × ÷ * / ^ ( ) ! %`, decimals, `1.5E3`, `π` or `pi`, `e`, `phi`, `tau`, `√x`, `sqrt(x)`, `root(n, x)`.

Implicit multiplication works: `2x`, `3(x+1)`, `2pi`. It has the same priority as `*`, so `1/2x` means `(1/2)·x`. `-2^2` is `-4`.

Functions: sqrt, root, abs, sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, ln, log(x), log(b, x), exp, mean, min, max, floor, ceil, sgn, fact, gcd, lcm, mod, ncr, npr, delta(a, b, c).

Percent: `x%` is x/100. `200 + 10%` is 220. `200 − 10%` is 180.

Missing closing brackets at the end are added automatically. An extra `)` is an error.

## Variables and functions

| You type | Result |
|---|---|
| `a := 5` | Saves variable `a` (its value at that moment) |
| `f(x) = x^2 + 1` | Defines a new function `f` |
| `f(x) := x^3` | Replaces an existing function |
| `f(2)` | Calls it |
| `ans` | The previous result |

Names use letters and `_` only, so `a1` means `a·1`. Names are case-sensitive and built-in names are reserved. Deleting or renaming warns if something else depends on it. The **Workspace** screen lists everything saved.

## Solving

| Type | Example |
|---|---|
| Equation | `x^2 - 5x + 6 = 0` or `solve(x^2-5x+6=0)` |
| Inequality | `x^2 - 4 < 0` (also `>`, `<=`, `>=`, `!=`) |
| Quadratic tool | `quad(1, -5, 6)` shows Δ, roots, vertex, axis |
| System | `x + y = 3; x - y = 1` |
| Simplify / expand / factor | `simplify(...)`, `expand((x+1)^2)`, `factor(x^2-1)` |
| Evaluate with values | `eval(x^2 + y; x = 2, y = 3)` |

Tap **Steps** under a result to see the derivation. Unsupported input shows a clear message instead of a guess.

## Graphing

Tap **Graph** on the calculator, or **Graph** next to a result. Enter `f(x)`, `y = ...` or just an expression.

- Drag to pan, pinch to zoom.
- Several functions can be shown at once.
- Roots, vertex or extrema, and the y-intercept are marked for polynomials.
- Breaks are drawn at discontinuities such as `1/x`.

## Keypad layers

Shift, Alt and Ctrl are toggles. They stay on until you tap them again, and combining them never clears your expression.

| Layer | Contains |
|---|---|
| Standard | Digits, operators, brackets |
| Alt | x y z a–g, relation symbols, `:=` |
| Shift | √, ⁿ√, x², xʸ, trig and inverse trig, ln, log, n!, \|x\|, mean, Δ, x₁,₂ |
| Ctrl | Define, edit and delete variables and functions; insert saved items; open workspace, history, graph |
| Shift + Alt | Templates such as x², sin x, f(x) |
| Ctrl + Alt | Solve, Inequality, Quad, System, Eval, Simplify, Expand, Factor, Graph |
| Shift + Ctrl | Save result as variable, save expression as function, Ans, Copy, Paste, clear variables, functions or history |
| Ctrl + Shift + Alt | φ, τ, gcd, lcm, mod, nCr, nPr, sinh, cosh, tanh, log_b |

A hardware keyboard works too: digits and operators type directly, Enter evaluates, Backspace deletes.

## Errors

Division by zero, domain errors, syntax errors, undefined names, overflow and timeouts each show a specific message. The expression stays in place so you can correct it.

## Fractions

Press the **a⁄b** key (standard keypad, row 4). If a number, name or bracket is just before the cursor it becomes the numerator and the cursor jumps into the denominator: `3` then **a⁄b** then `4` gives `(3)/(4)` = 3/4.
On an empty line it inserts an empty `()/()` template. The ◀ ▶ keys hop across the divider in one step.
Exact results that are fractions are shown stacked (numerator over denominator).

## Automatic function notation

A linear expression in one unknown without function notation is turned into a function and saved automatically:

| You type | You get |
|---|---|
| `2x + 3` | `f(x) = 2x + 3` (saved, with a Graph button) |
| `y = 5x - 1` | `g(x) = 5x - 1` (next free name) |
| `y = t/2 + 1` | `f(t) = t/2 + 1` |

`y = ...` always becomes a function. Expressions that are not polynomials, such as `x^1.1`, `sin(x)` or `2^x`, also become functions, so they can be evaluated (`f(4)`) and graphed.
Ordinary polynomials such as `(x+1)^2` are still expanded as before, and equations like `2x + 1 = 5` are still solved.
`solve`, `expand` and `factor` need whole-number powers; with something like `x^1.1` they explain this instead of failing silently.

## Sequences and patterns

- `arith(a₁, d, n)`, `arithsum(a₁, d, n)`: n-th term and sum of an arithmetic sequence
- `geom(a₁, r, n)`, `geomsum(a₁, r, n)`, `geominf(a₁, r)`: geometric term, sum, and sum to infinity (|r| < 1)
- `seq(2, 6, 18, 54)`: detects arithmetic, geometric, quadratic and Fibonacci-type patterns and shows the formula, the next terms and sums

## Algebraic identities (special products)

`expand((x+3)^2)` names the identity it used. The **Tools → Identities** list has 15 identities (squares, cubes, higher powers, products) and a **Use** button that puts a ready example in the calculator. Every identity in the list is checked by the engine itself.

## Tools screen

Open it from the **Tools** chip on the calculator.
- **Unit circle:** slide or tap to pick an angle; exact sin, cos, tan and the radian value at multiples of 30° and 45°, approximate values elsewhere.
- **Shapes:** 12 flat shapes and 5 solids; enter sizes (fractions and √ allowed) to get areas, perimeters, volumes and surface areas, exact where possible, with a drawing.
- **Charts:** bar, line and pie charts from your own numbers or `label:value` pairs, plus count, sum, mean, median, mode, range, variance and standard deviation.
- **Sequences:** pattern finder and a generator for arithmetic and geometric sequences.
- **Identities:** the special-product reference.

## Plus or minus (±)

The **±** key is on the Shift layer (bottom row, in the place of `sgn`). `5 ± 2` shows both results: `5 + 2 = 7` and `5 - 2 = 3`. A leading `±3` gives `3` and `-3`. Up to three ± signs can be used in one calculation, and ± cannot be used inside a definition.


## Version 1.2

### Chained comparisons
Type `4 < 2 + x < 6` and press `=`. The app splits it into `4 < 2 + x` and `2 + x < 6`, solves each and keeps the overlap: `2 < x < 4`. Mixed signs work (`1 < x <= 3`), and so do polynomials (`1 < x^2 < 4` gives two intervals). With no unknown it answers True or False.

### Home page
The Home page opens after the intro (change this in Settings, "Start page"). Tiles open every screen, "Try an example" runs a ready-made input in one tap, and "Recent" reopens earlier calculations. The Back button returns to Home. In the calculator, the "Home" chip also goes there.

### Letters
Tap **ABC** above the keypad. Page 1 has a–t, page 2 has u–z and Greek letters (α β γ θ λ μ ρ σ ω η κ ν ψ χ, typed as alpha, beta, ...), page 3 has symbols. The first key of the bottom row switches pages; tap ABC again to return.

### Calculus
`diff(x^3 + 2x, x)` gives `3x^2 + 2`; `diff(x^2, x, 3)` gives 6. `integrate(3x^2, x)` gives `x^3 + C`; `integrate(x^2, x, 0, 3)` gives 9. Trig functions follow the DEG/RAD setting. If no closed form exists, give bounds to get a numeric value.

### Numbers
`isprime(97)`, `pfactor(360)`, `divisors(28)`, `totient(36)`, `nextprime(100)`, `bin(10)`, `hex(255)`, `tobase(255, 5)`, `frombase(1010, 2)`. These are on the Ctrl+Shift+Alt key layer too.

### Tools
Calculus, Numbers, Matrices (determinant, inverse, rank, powers, A+B, A×B, solve A x = B, all exact), Units (length, mass, time, area, volume, speed, temperature, angle, data, energy, pressure; exact).
