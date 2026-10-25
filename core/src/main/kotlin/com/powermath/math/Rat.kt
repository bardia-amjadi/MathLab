package com.powermath.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

enum class ErrorKind { SYNTAX, UNDEFINED, DOMAIN, DIV_ZERO, UNSUPPORTED, LIMIT, NON_REAL, DEFINITION }

/** The only exception type the engine throws for user-visible problems. */
class MathError(val kind: ErrorKind, message: String, val position: Int = -1) : Exception(message)

/** Exact rational number with BigInteger parts. Always stored reduced with a positive denominator. */
class Rat private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rat> {
    companion object {
        const val MAX_BITS = 200_000
        val ZERO = Rat(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rat(BigInteger.ONE, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Rat {
            if (d.signum() == 0) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
            var nn = n
            var dd = d
            if (dd.signum() < 0) { nn = nn.negate(); dd = dd.negate() }
            val g = nn.gcd(dd)
            if (g != BigInteger.ONE) { nn = nn.divide(g); dd = dd.divide(g) }
            if (nn.bitLength() + dd.bitLength() > MAX_BITS) throw MathError(ErrorKind.LIMIT, "Number is too large")
            return Rat(nn, dd)
        }

        fun of(n: Long, d: Long = 1L): Rat = of(BigInteger.valueOf(n), BigInteger.valueOf(d))
        fun of(n: Int): Rat = of(BigInteger.valueOf(n.toLong()), BigInteger.ONE)

        /** Exact decimal literal: intPart.fracPart x 10^exp */
        fun fromDecimal(intPart: String, fracPart: String, exp: Int): Rat {
            if (Math.abs(exp) > 50_000) throw MathError(ErrorKind.LIMIT, "Exponent is too large")
            val digits = BigInteger((intPart + fracPart).ifEmpty { "0" })
            val e = exp - fracPart.length
            return if (e >= 0) of(digits.multiply(BigInteger.TEN.pow(e)), BigInteger.ONE)
            else of(digits, BigInteger.TEN.pow(-e))
        }
    }

    val signum: Int get() = num.signum()
    val isInteger: Boolean get() = den == BigInteger.ONE
    val isZero: Boolean get() = num.signum() == 0

    operator fun plus(o: Rat) = of(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den))
    operator fun minus(o: Rat) = of(num.multiply(o.den).subtract(o.num.multiply(den)), den.multiply(o.den))
    operator fun times(o: Rat) = of(num.multiply(o.num), den.multiply(o.den))
    operator fun div(o: Rat): Rat {
        if (o.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
        return of(num.multiply(o.den), den.multiply(o.num))
    }
    operator fun unaryMinus() = Rat(num.negate(), den)
    fun abs() = if (num.signum() < 0) -this else this
    fun inverse() = ONE / this

    fun pow(n: Int): Rat {
        if (n == 0) return ONE
        val k = Math.abs(n)
        val bits = (num.bitLength() + den.bitLength()).toLong() * k
        if (bits > MAX_BITS) throw MathError(ErrorKind.LIMIT, "Result is too large")
        val p = of(num.pow(k), den.pow(k))
        return if (n < 0) p.inverse() else p
    }

    fun floor(): BigInteger {
        val qr = num.divideAndRemainder(den)
        return if (qr[1].signum() < 0) qr[0].subtract(BigInteger.ONE) else qr[0]
    }

    fun toBigDecimal(mc: MathContext): BigDecimal =
        if (isInteger) BigDecimal(num) else BigDecimal(num).divide(BigDecimal(den), mc)

    fun toDouble(): Double = if (isInteger) num.toDouble() else toBigDecimal(MathContext(30)).toDouble()

    override fun compareTo(other: Rat): Int = num.multiply(other.den).compareTo(other.num.multiply(den))
    override fun equals(other: Any?) = other is Rat && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) num.toString() else "$num/$den"
}

fun Int.toRat(): Rat = Rat.of(this)
fun Long.toRat(): Rat = Rat.of(this)
