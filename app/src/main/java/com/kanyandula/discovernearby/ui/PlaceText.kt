package com.kanyandula.discovernearby.ui

import androidx.annotation.StringRes
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.AttributeType

private const val METERS_PER_KM = 1_000.0
internal const val SEPARATOR = " · "

/** Kilometres, for the "%.1f km" strings. */
internal fun kilometres(meters: Int) = meters / METERS_PER_KM

/** The name of each kind `CategoryConfigs` targets ("cafe" → "Café"); a kind not listed shows no label. */
private val KIND_LABELS = mapOf(
    "coffee_shop" to R.string.kind_coffee_shop,
    "cafe" to R.string.kind_cafe,
    "restaurant" to R.string.kind_restaurant,
    "takeaway" to R.string.kind_takeaway,
    "fast_food" to R.string.kind_fast_food,
    "park" to R.string.kind_park,
    "beach" to R.string.kind_beach,
    "forest" to R.string.kind_forest,
    "trail" to R.string.kind_trail,
    "hiking_area" to R.string.kind_hiking_area,
    "waterfall" to R.string.kind_waterfall,
    "outdoor_attraction" to R.string.kind_outdoor_attraction,
    "zoo" to R.string.kind_zoo,
    "aquarium" to R.string.kind_aquarium,
    "amusement_park" to R.string.kind_amusement_park,
    "water_park" to R.string.kind_water_park,
    "childrens_museum" to R.string.kind_childrens_museum,
    "playground" to R.string.kind_playground,
    "family_attraction" to R.string.kind_family_attraction,
    "viewpoint" to R.string.kind_viewpoint,
    "scenic_spot" to R.string.kind_scenic_spot,
    "coastal_overlook" to R.string.kind_coastal_overlook,
    "natural_attraction" to R.string.kind_natural_attraction,
    "tourist_attraction" to R.string.kind_tourist_attraction,
    "landmark" to R.string.kind_landmark,
    "museum" to R.string.kind_museum,
    "heritage_site" to R.string.kind_heritage_site,
)

/** The label for a normalised kind, or null for one without a name. */
@StringRes
internal fun kindLabel(kind: String?): Int? = KIND_LABELS[kind]

@get:StringRes
internal val AttributeType.label: Int
    get() = when (this) {
        AttributeType.PARKING -> R.string.attribute_parking
        AttributeType.TOILETS -> R.string.attribute_toilets
        AttributeType.CAFE -> R.string.attribute_cafe
        AttributeType.PLAYGROUND -> R.string.attribute_playground
        AttributeType.TRAILS -> R.string.attribute_trails
        AttributeType.BEACH -> R.string.attribute_beach
        AttributeType.VIEWPOINT -> R.string.attribute_viewpoint
        AttributeType.MUSEUM -> R.string.attribute_museum
        AttributeType.FAMILY_FRIENDLY -> R.string.attribute_family_friendly
        AttributeType.DRIVE_THROUGH -> R.string.attribute_drive_through
    }
