package com.kanyandula.discovernearby.ui

import androidx.annotation.StringRes
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.model.AttributeType

internal const val METERS_PER_KM = 1_000.0
internal const val SEPARATOR = " · "

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
