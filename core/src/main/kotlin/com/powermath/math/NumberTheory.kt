package com.powermath.math

import java.math.BigInteger

/** Primes, factorisation, divisors and number bases. All exact (BigInteger). */
object NumberTheory {
    private val LIMIT = BigInteger.TEN.pow(40)

    fun toBig(v: Value, what: String = "The number"): BigInteger {
        if (v !is Value.Q || !v.r.isInteger) throw MathError(ErrorKind.DOMAIN, "$what must be a whole number")
        return v.r.num
    }

    private fun positive(n: BigInteger): BigInteger {
        if (n.signum() <= 0) throw MathError(ErrorKind.DOMAIN, "The number must be positive")
        if (n > LIMIT) throw MathError(ErrorKind.LIMIT, "The number is too large (limit 10^40)")
        return n
    }

    fun isPrime(n: BigInteger): Boolean {
        if (n < BigInteger.valueOf(2)) return false
        return n.isProbablePrime(50)
    }


    /** Prime factorisation as (prime, exponent). Trial division, then Pollard's rho for large cofactors. */
    fun factorize(input: BigInteger): List<Pair<BigInteger, Int>> {
        var n = positive(input)
        val out = ArrayList<Pair<BigInteger, Int>>()
        fun take(p: BigInteger) { var e = 0; while (n.mod(p).signum() == 0) { n = n.divide(p); e++ }; if (e > 0) out.add(Pair(p, e)) }
        take(BigInteger.valueOf(2)); take(BigInteger.valueOf(3)); take(BigInteger.valueOf(5))
        var p = 7L
        val inc = longArrayOf(4, 2, 4, 2, 4, 6, 2, 6)
        var i = 0
        while (p <= 100_000 && BigInteger.valueOf(p).pow(2) <= n) { take(BigInteger.valueOf(p)); p += inc[i]; i = (i + 1) % 8 }
        val rest = ArrayList<BigInteger>()
        fun split(m: BigInteger, depth: Int) {
            if (m == BigInteger.ONE) return
            if (m.isProbablePrime(50)) { rest.add(m); return }
            val d = rho(m, depth) ?: throw MathError(ErrorKind.LIMIT, "This number is too hard to factor here")
            split(d, depth + 1); split(m.divide(d), depth + 1)
        }
        if (n > BigInteger.ONE) split(n, 0)
        for ((q, e) in rest.groupBy { it }.map { Pair(it.key, it.value.size) }.sortedBy { it.first }) out.add(Pair(q, e))
        return out.sortedBy { it.first }
    }

    private fun rho(n: BigInteger, seed: Int): BigInteger? {
        if (n.mod(BigInteger.valueOf(2)).signum() == 0) return BigInteger.valueOf(2)
        var c = BigInteger.valueOf(1L + seed)
        val deadline = System.nanoTime() + 3_000_000_000L
        repeat(20) {
            var x = BigInteger.valueOf(2); var y = x; var d = BigInteger.ONE
            while (d == BigInteger.ONE) {
                x = x.multiply(x).add(c).mod(n); y = y.multiply(y).add(c).mod(n); y = y.multiply(y).add(c).mod(n)
                d = x.subtract(y).abs().gcd(n)
                if (System.nanoTime() > deadline) return null
            }
            if (d != n) return d
            c = c.add(BigInteger.ONE)
        }
        return null
    }

    fun factorText(f: List<Pair<BigInteger, Int>>): String =
        f.joinToString(" × ") { (p, e) -> if (e == 1) p.toString() else "$p^$e" }

    fun divisors(n: BigInteger): List<BigInteger> {
        val f = factorize(n)
        var ds = listOf(BigInteger.ONE)
        for ((p, e) in f) {
            val next = ArrayList<BigInteger>()
            var pk = BigInteger.ONE
            for (k in 0..e) { for (d in ds) next.add(d.multiply(pk)); pk = pk.multiply(p) }
            ds = next
            if (ds.size > 100_000) throw MathError(ErrorKind.LIMIT, "Too many divisors to list")
        }
        return ds.sorted()
    }

    fun totient(n: BigInteger): BigInteger {
        var r = positive(n)
        for ((p, _) in factorize(n)) r = r.divide(p).multiply(p.subtract(BigInteger.ONE))
        return r
    }

    fun nextPrime(n: BigInteger): BigInteger = n.max(BigInteger.ONE).nextProbablePrime()

    // ---------------- bases ----------------
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun toBase(n: BigInteger, base: Int): String {
        if (base < 2 || base > 36) throw MathError(ErrorKind.DOMAIN, "The base must be between 2 and 36")
        if (n.bitLength() > 4096) throw MathError(ErrorKind.LIMIT, "The number is too large")
        return n.toString(base).uppercase()
    }

    /** digits typed as a decimal number (e.g. 1010 for binary) are read in the given base */
    fun fromBase(digits: BigInteger, base: Int): BigInteger {
        if (base < 2 || base > 10) throw MathError(ErrorKind.DOMAIN, "Typed digits can only be read in bases 2 to 10")
        val s = digits.abs().toString()
        for (ch in s) if (ch - '0' >= base) throw MathError(ErrorKind.DOMAIN, "The digit $ch does not exist in base $base")
        val v = BigInteger(s, base)
        return if (digits.signum() < 0) v.negate() else v
    }

    /** Parses text such as "FF" in the given base (for the Tools screen). */
    fun parseInBase(text: String, base: Int): BigInteger {
        val t = text.trim().uppercase().replace(" ", "")
        if (t.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Enter a number")
        if (base < 2 || base > 36) throw MathError(ErrorKind.DOMAIN, "The base must be between 2 and 36")
        val neg = t.startsWith("-")
        val body = t.removePrefix("-")
        for (ch in body) { val d = DIGITS.indexOf(ch); if (d < 0 || d >= base) throw MathError(ErrorKind.DOMAIN, "'$ch' is not a digit in base $base") }
        val v = BigInteger(body, base)
        return if (neg) v.negate() else v
    }
}
