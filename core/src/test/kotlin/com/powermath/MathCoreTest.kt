package com.powermath

import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the framework-free CoreSuite (also runnable with plain kotlinc via CoreSuiteKt.main). */
class MathCoreTest {
    @Test
    fun allMathCoreChecksPass() {
        val results = CoreSuite.run()
        val failed = results.filter { it.error != null }
        assertTrue(
            "${failed.size} of ${results.size} checks failed:\n" + failed.joinToString("\n") { "  ${it.name}: ${it.error}" },
            failed.isEmpty()
        )
    }
}
