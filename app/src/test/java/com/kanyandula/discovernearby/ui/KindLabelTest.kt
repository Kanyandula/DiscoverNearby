package com.kanyandula.discovernearby.ui

import com.kanyandula.discovernearby.discovery.CategoryConfigs
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KindLabelTest {

    // A kind the ranking targets but the UI can't name would show a row with no kind; this catches a new kind.
    @Test
    fun everyTargetedKindHasALabel() {
        val unnamed = CategoryConfigs.values.flatMap { it.kindWeights.keys }.toSet().filter { kindLabel(it) == null }
        assertTrue("Kinds without a label: $unnamed", unnamed.isEmpty())
    }

    @Test
    fun anUnknownKindHasNoLabel() {
        assertNull(kindLabel("unknown_kind"))
        assertNull(kindLabel(null))
    }
}
