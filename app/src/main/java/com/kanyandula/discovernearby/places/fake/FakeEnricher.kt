package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.model.PlaceEnrichment
import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.PriceLevel
import com.kanyandula.discovernearby.places.PlaceEnricher

/**
 * Made-up Tripadvisor-style prices and descriptions for some fake places (DN-UX-005), so Place Details is built and
 * checked on the `--es scenario` fakes without paid calls. No photo or rating: those need the network.
 */
object FakeEnricher : PlaceEnricher {

    private val byTemplate = mapOf(
        "The Daily Grind" to PlaceEnrichment(
            priceLevel = PriceLevel.CHEAP,
            description = "A specialty coffee shop with locally roasted beans, light bites and a relaxed atmosphere.",
        ),
        "Harbour Roasters" to PlaceEnrichment(
            priceLevel = PriceLevel.MID,
            description = "Small-batch roastery by the harbour.",
        ),
        // Long on purpose: it must stop at two lines.
        "Seaview Kitchen" to PlaceEnrichment(
            priceLevel = PriceLevel.FINE,
            description = "Seasonal plates from local growers and the morning's catch, served in a bright room " +
                "over the bay, with a short wine list, children's portions and a garden terrace open from May " +
                "to September when the weather allows.",
        ),
        "Little Acres Farm" to PlaceEnrichment(
            description = "A working farm where children can feed the animals and play in the hay barn.",
        ),
    )

    // Fake names are "{template}, {town}" (fakePlaces).
    override suspend fun enrich(place: PlaceSummary, category: DiscoveryCategory): PlaceEnrichment? =
        byTemplate[place.name.substringBefore(", ")]
}
