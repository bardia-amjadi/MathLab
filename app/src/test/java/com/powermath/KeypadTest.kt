package com.powermath

import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadTest {
    @Test
    fun keypadLayersAndModifierStateMachine() {
        val results = KeypadChecks.run()
        val failed = results.filter { it.error != null }
        assertTrue("${failed.size} of ${results.size} failed:\n" + failed.joinToString("\n") { "  ${it.name}: ${it.error}" }, failed.isEmpty())
    }
}
