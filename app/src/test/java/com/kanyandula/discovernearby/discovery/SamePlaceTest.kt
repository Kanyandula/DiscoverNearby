package com.kanyandula.discovernearby.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// DN-M2-004 (Product Lead, 2026-10-11): one place when within 150 m, a shared phone, and either within 10 m or one
// name containing the other. Made-up names in the shapes the benchmark found.
class SamePlaceTest {

    // Each record its own ID, as HERE gives them: the same ID is one place without any other test.
    private fun place(name: String, metersNorth: Int, vararg phones: String) =
        testPlace("$name@$metersNorth", "aquarium", metersNorth = metersNorth)
            .copy(name = name, phones = phones.toSet())

    @Test
    fun twoNamesOnOneSpotWithOnePhoneAreOnePlace() {
        assertTrue(place("Bayside Aquaria", 100, "+1").isSamePlaceAs(place("National Sea Centre", 105, "+1")))
    }

    @Test
    fun theSameNameAPairOfStreetsApartIsOnePlace() {
        assertTrue(place("BLUEWAVE HALL", 100, "+1").isSamePlaceAs(place("BLUEWAVE HALL", 238, "+1")))
    }

    @Test
    fun oneNameInsideTheOtherIsOnePlace() {
        assertTrue(place("Lakeside Zoological Soc", 100, "+1").isSamePlaceAs(place("Lakeside Zoo", 146, "+1")))
        assertTrue(place("Aquatic Centre", 100, "+1").isSamePlaceAs(place("Splash at Aquatic Centre", 126, "+1")))
        assertTrue(place("Café Lúb", 100, "+1").isSamePlaceAs(place("CAFE LUB!", 160, "+1"))) // accents, case, marks
    }

    // Neighbours with one owner's number, different names, 27 m apart.
    @Test
    fun aSharedPhoneAloneIsNotEnough() {
        assertFalse(place("Harbour Tavern", 100, "+1").isSamePlaceAs(place("Quayside & Co", 127, "+1")))
    }

    // Records of one beach with no contacts at all.
    @Test
    fun recordsWithoutAPhoneStaySeparate() {
        assertFalse(place("Silverspray Strand", 100).isSamePlaceAs(place("Silverspray Strand", 101)))
    }

    @Test
    fun farApartIsNeverOnePlace() {
        assertFalse(place("BLUEWAVE HALL", 100, "+1").isSamePlaceAs(place("BLUEWAVE HALL", 260, "+1")))
    }

    // Containment needs four letters, so "Bo" isn't inside every name with a "bo".
    @Test
    fun aVeryShortNameDoesNotMatchInsideAnother() {
        assertFalse(place("Bo", 100, "+1").isSamePlaceAs(place("Bo Bar", 130, "+1")))
    }

    @Test
    fun theSameIdIsAlwaysOnePlace() {
        assertTrue(testPlace("a", "zoo").isSamePlaceAs(testPlace("a", "zoo", metersNorth = 5_000)))
    }
}
