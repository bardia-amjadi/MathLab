package com.powermath.math

/** Exact rational matrices for the Matrix tool. */
class Matrix(val rows: List<List<Rat>>) {
    val m get() = rows.size
    val n get() = if (rows.isEmpty()) 0 else rows[0].size
    val isSquare get() = m == n

    companion object {
        const val MAX = 8

        /** "1 2; 3 4", "1,2;3,4" or one row per line. Entries may be fractions such as 1/2 or decimals. */
        fun parse(text: String): Matrix {
            val rowsText = text.trim().split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (rowsText.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Enter a matrix such as 1 2; 3 4")
            val rows = rowsText.map { line ->
                line.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.map { entry ->
                    try { parseEntry(entry) } catch (e: NumberFormatException) { throw MathError(ErrorKind.SYNTAX, "'$entry' is not a number") }
                }
            }
            if (rows.any { it.size != rows[0].size }) throw MathError(ErrorKind.SYNTAX, "All rows need the same number of entries")
            if (rows.size > MAX || rows[0].size > MAX) throw MathError(ErrorKind.LIMIT, "Matrices up to ${MAX}×$MAX are supported")
            return Matrix(rows)
        }

        private fun parseEntry(s: String): Rat {
            if (s.contains('/')) { val (a, b) = s.split('/', limit = 2); val d = parseEntry(b); if (d.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero"); return parseEntry(a) / d }
            val neg = s.startsWith("-")
            val t = s.removePrefix("-").removePrefix("+")
            val parts = t.split('.')
            if (parts.size > 2 || t.isEmpty() || parts.any { p -> p.any { !it.isDigit() } }) throw NumberFormatException(s)
            val r = Rat.fromDecimal(parts[0], parts.getOrElse(1) { "" }, 0)
            return if (neg) -r else r
        }

        fun identity(k: Int) = Matrix(List(k) { i -> List(k) { j -> if (i == j) Rat.ONE else Rat.ZERO } })
    }

    fun transpose() = Matrix(List(n) { j -> List(m) { i -> rows[i][j] } })

    operator fun times(o: Matrix): Matrix {
        if (n != o.m) throw MathError(ErrorKind.DOMAIN, "A ${m}×$n matrix cannot be multiplied by a ${o.m}×${o.n} matrix")
        return Matrix(List(m) { i -> List(o.n) { j -> var s = Rat.ZERO; for (k in 0 until n) s += rows[i][k] * o.rows[k][j]; s } })
    }

    operator fun plus(o: Matrix): Matrix {
        if (m != o.m || n != o.n) throw MathError(ErrorKind.DOMAIN, "Matrices must have the same size to be added")
        return Matrix(List(m) { i -> List(n) { j -> rows[i][j] + o.rows[i][j] } })
    }

    operator fun minus(o: Matrix): Matrix {
        if (m != o.m || n != o.n) throw MathError(ErrorKind.DOMAIN, "Matrices must have the same size to be subtracted")
        return Matrix(List(m) { i -> List(n) { j -> rows[i][j] - o.rows[i][j] } })
    }

    fun scale(k: Rat) = Matrix(rows.map { r -> r.map { it * k } })

    fun pow(k: Int): Matrix {
        if (!isSquare) throw MathError(ErrorKind.DOMAIN, "Only square matrices have powers")
        if (k < 0 || k > 64) throw MathError(ErrorKind.DOMAIN, "The power must be between 0 and 64")
        var r = identity(n)
        repeat(k) { r = r * this }
        return r
    }

    private fun requireSquare() { if (!isSquare) throw MathError(ErrorKind.DOMAIN, "This needs a square matrix") }

    /** Gaussian elimination with exact fractions: returns (reduced rows, rank, determinant sign factor, pivot columns) */
    private class Elim(val a: Array<Array<Rat>>, val rank: Int, val detFactor: Rat, val pivots: List<Int>)

    private fun eliminate(extra: Int = 0, source: Array<Array<Rat>>? = null): Elim {
        val a = source ?: Array(m) { i -> Array(n) { j -> rows[i][j] } }
        val cols = a[0].size
        var rank = 0
        var det = Rat.ONE
        val pivots = ArrayList<Int>()
        for (c in 0 until (cols - extra)) {
            var p = -1
            for (r in rank until m) if (!a[r][c].isZero) { p = r; break }
            if (p < 0) { det = Rat.ZERO; continue }
            if (p != rank) { val t = a[p]; a[p] = a[rank]; a[rank] = t; det = -det }
            val pv = a[rank][c]
            det = det * pv
            for (j in 0 until cols) a[rank][j] = a[rank][j] / pv
            for (r in 0 until m) if (r != rank && !a[r][c].isZero) {
                val f = a[r][c]
                for (j in 0 until cols) a[r][j] = a[r][j] - f * a[rank][j]
            }
            pivots.add(c); rank++
        }
        return Elim(a, rank, det, pivots)
    }

    fun rank(): Int = eliminate().rank

    fun determinant(): Rat {
        requireSquare()
        if (n > MAX) throw MathError(ErrorKind.LIMIT, "Too large")
        val e = eliminate()
        return if (e.rank < n) Rat.ZERO else e.detFactor
    }

    fun inverse(): Matrix {
        requireSquare()
        val aug = Array(n) { i -> Array(2 * n) { j -> if (j < n) rows[i][j] else if (j - n == i) Rat.ONE else Rat.ZERO } }
        val e = eliminate(extra = n, source = aug)
        if (e.rank < n) throw MathError(ErrorKind.DOMAIN, "The matrix is singular (determinant 0) and has no inverse")
        return Matrix(List(n) { i -> List(n) { j -> e.a[i][n + j] } })
    }

    fun rref(): Matrix = Matrix(eliminate().a.map { it.toList() })

    fun trace(): Rat { requireSquare(); var s = Rat.ZERO; for (i in 0 until n) s += rows[i][i]; return s }

    /** Solves A x = b for a square, non-singular A (b is a column given as a one-column matrix). */
    fun solve(b: Matrix): Matrix {
        if (!isSquare || b.n != 1 || b.m != n) throw MathError(ErrorKind.DOMAIN, "A must be square and b a single column with the same number of rows")
        return inverse() * b
    }

    /** Text rows with aligned columns, e.g. "│ 1  2 │". */
    fun toLines(): List<String> {
        val cells = rows.map { r -> r.map { it.toString() } }
        val w = List(n) { j -> cells.maxOf { it[j].length } }
        return cells.map { r -> "[ " + r.indices.joinToString("  ") { j -> r[j].padStart(w[j]) } + " ]" }
    }
}
