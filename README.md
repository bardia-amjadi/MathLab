# Power Math

An offline scientific calculator for Android with exact fractions, symbolic algebra, equation and inequality solving, graphing, saved variables and functions, and history.

Built with Kotlin, Jetpack Compose (Material 3), Room and a simple MVVM structure. No internet permission, no ads, no tracking.

## Download the APK

1. Open the **Actions** tab of this repository.
2. Choose **Build APK** and click **Run workflow** (or open the latest successful run).
3. Download the **PowerMath-apk** artifact, unzip it, and install `app-release.apk` on your phone.

Full step-by-step instructions, with no Android Studio needed: [docs/BUILD.md](docs/BUILD.md)

## Status (please read)

| Part | Status |
|---|---|
| Math engine in `core/` (parser, exact arithmetic, solver, formatter, graph sampling, editor logic) | Compiled and tested, 150/150 checks pass (`docs/core-test-results.txt`) |
| Keypad layers and modifier logic | Compiled and tested, 25/25 checks pass (`docs/keypad-test-results.txt`) |
| Android app in `app/` (Compose UI, ViewModel, Room, settings, graph canvas) | The first version built on GitHub Actions. Screens added since (Tools, Home, intro, settings, v1.2 tools) have **not been compiled yet**; the first build may need small fixes. |
| Release APK | Not built yet. It is produced by the GitHub Actions workflow. |

If the workflow fails, open the failed run, copy the red error lines, and fix or report them. Typical problems are a missing import or a Compose signature.

## Features

- Scientific calculator with fraction-first exact results, plus Decimal and Scientific display modes
- Exact forms: fractions, `a + b√n`, multiples of π, exact trig for 30°/45° angles
- Symbolic: `simplify`, `expand`, `factor`
- Solvers: linear, quadratic (shows Δ), polynomial equations, polynomial inequalities, linear systems, all with steps
- Interactive graph: pan, zoom, several functions, roots and extrema, gaps at discontinuities
- Variables and user functions that persist (Room), history, settings
- Fraction entry key (a⁄b) with stacked exact results
- Automatic function notation: `2x + 3` or `y = 2x + 3` becomes `f(x) = 2x + 3`
- Sequences and patterns (arithmetic, geometric, quadratic, Fibonacci-type) and algebraic identities (special products)
- Tools screen: unit circle, geometric shapes and solids, bar/line/pie charts with statistics
- Shift / Alt / Ctrl keypad layers, DEG / RAD, hardware keyboard support

How to type things and what each key layer does: [docs/USER_GUIDE.md](docs/USER_GUIDE.md)

## Project layout

```
core/   Pure Kotlin/JVM math engine, no Android dependencies
app/    Android app: data (Room), viewmodel, ui (Compose screens, keypad, dialogs)
docs/   Build guide, user guide, test output
.github/workflows/build-apk.yml   Cloud build of the APK
```

All features (calculator, solver, simplifier, graph) share one parser and expression tree. Heavy work runs off the main thread with a 5 second deadline.

## New in 1.2

- Chained comparisons: `4 < 2 + x < 6` gives `2 < x < 4`; also `1 < x^2 < 4`, `-1 <= 3x+2 <= 11`
- Home page with one-tap examples, recent calculations and tiles for every part of the app
- ABC letter keyboard: all letters a–z, Greek letters and symbols
- Calculus: `diff(f, x)`, `diff(f, x, a)`, `integrate(f, x)`, `integrate(f, x, a, b)` (exact when possible, numeric otherwise)
- Number theory and bases: `isprime`, `pfactor`, `divisors`, `totient`, `nextprime`, `bin`, `oct`, `hex`, `tobase`, `frombase`
- Tools: Calculus, Numbers, Matrices (exact), Units (exact conversion)
- New animated intro, more settings (start page, animations, font, live preview, keep screen on, reset) and more animations

## Limitations

- Equations: polynomials with rational coefficients in one unknown; systems must be linear. Equations with fractions in x, radicals, trig or exponentials report "unsupported" instead of guessing.
- Degree is limited to 24. Roots that are not exact are numeric and shown with ≈.
- Inequalities are single-variable polynomial. Chained comparisons such as `4 < 2 + x < 6` are supported (they are split, solved and intersected).
- Complex numbers only appear as information for quadratics with Δ < 0.
- History is capped at 1000 rows.

## Run the math tests without Gradle

Needs only `kotlinc` 2.0.x and a JDK:

```
kotlinc -jvm-target 17 -d out/main $(find core/src/main -name '*.kt')
kotlinc -jvm-target 17 -d out/test -cp out/main core/src/test/kotlin/com/powermath/CoreSuite.kt
java -cp out/main:out/test:$KOTLIN_HOME/lib/kotlin-stdlib.jar com.powermath.CoreSuiteKt
```

## Versions

AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01, Room 2.6.1. compileSdk and targetSdk 35, minSdk 26. These were pinned but never resolved in the authoring environment.

## License

No license has been chosen yet. Add a `LICENSE` file (for example MIT) before sharing the code publicly.
