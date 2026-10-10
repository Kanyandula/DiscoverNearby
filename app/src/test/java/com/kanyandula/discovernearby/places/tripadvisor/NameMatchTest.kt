package com.kanyandula.discovernearby.places.tripadvisor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Made-up names (public repo): the shapes HERE and Tripadvisor names take for one place.
class NameMatchTest {

    private fun sameName(a: String, b: String) = nameMatch(a, b) > 0

    @Test
    fun theTradeWordsAndCaseDontMatter() {
        assertTrue(sameName("HARBOUR COFFEE", "Harbour"))
        assertTrue(sameName("Café Lumen", "Cafe Lumen"))
        assertTrue(sameName("The Copper Kettle", "Copper Kettle Restaurant"))
    }

    @Test
    fun aTownAddedToTheNameStillMatches() {
        assertTrue(sameName("Juniper Pear", "Juniper Pear Seaview"))
        assertTrue(sameName("Seaview Beach", "Seaview South Beach"))
    }

    @Test
    fun nearlyTheSameSpellingMatches() {
        assertTrue(sameName("Marlowes Bakehouse", "Marlowe's Bakehouse"))
    }

    @Test
    fun neighboursDontMatch() {
        assertFalse(sameName("Seaview Beach", "Seaview Park")) // place words stay
        assertFalse(sameName("Seaview Beach", "Seaview Harbour"))
        assertFalse(sameName("Juniper Pear", "Juniper Lane Deli"))
        assertFalse(sameName("Harbour", "Harbour Lane Deli")) // two extra words: another place on Harbour Lane
        assertFalse(sameName("Seaview Studio 1", "Seaview Studio 2")) // branches differ by their number
    }

    @Test
    fun theSameWordsBeatAnAddedWord() {
        assertTrue(nameMatch("Juniper Pear", "Juniper Pear") > nameMatch("Juniper Pear", "Juniper Pear Seaview"))
    }

    @Test
    fun aNameOfOnlyTradeWordsMatchesNothing() {
        assertFalse(sameName("The Coffee Bar", "Coffee"))
        assertFalse(sameName("", "Harbour"))
    }
}
