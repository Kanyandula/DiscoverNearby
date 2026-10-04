package com.kanyandula.discovernearby.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.kanyandula.discovernearby.R
import com.kanyandula.discovernearby.discovery.DiscoveryCategory
import com.kanyandula.discovernearby.ui.theme.CoffeeTint
import com.kanyandula.discovernearby.ui.theme.ExploreTint
import com.kanyandula.discovernearby.ui.theme.FamilyTint
import com.kanyandula.discovernearby.ui.theme.FoodTint
import com.kanyandula.discovernearby.ui.theme.OutdoorsTint
import com.kanyandula.discovernearby.ui.theme.ScenicTint

/** How a category looks. Kept in ui/ so discovery/ stays free of Android resources. */
internal class CategoryVisual(
    @param:StringRes val label: Int,
    @param:StringRes val subtitle: Int,
    @param:DrawableRes val icon: Int,
    val tint: Color,
)

internal val DiscoveryCategory.visual: CategoryVisual
    get() = when (this) {
        DiscoveryCategory.COFFEE -> CategoryVisual(
            label = R.string.category_coffee,
            subtitle = R.string.category_coffee_subtitle,
            icon = R.drawable.ic_cat_coffee,
            tint = CoffeeTint,
        )
        DiscoveryCategory.FOOD -> CategoryVisual(
            label = R.string.category_food,
            subtitle = R.string.category_food_subtitle,
            icon = R.drawable.ic_cat_food,
            tint = FoodTint,
        )
        DiscoveryCategory.OUTDOORS -> CategoryVisual(
            label = R.string.category_outdoors,
            subtitle = R.string.category_outdoors_subtitle,
            icon = R.drawable.ic_cat_outdoors,
            tint = OutdoorsTint,
        )
        DiscoveryCategory.FAMILY -> CategoryVisual(
            label = R.string.category_family,
            subtitle = R.string.category_family_subtitle,
            icon = R.drawable.ic_cat_family,
            tint = FamilyTint,
        )
        DiscoveryCategory.SCENIC -> CategoryVisual(
            label = R.string.category_scenic,
            subtitle = R.string.category_scenic_subtitle,
            icon = R.drawable.ic_cat_scenic,
            tint = ScenicTint,
        )
        DiscoveryCategory.EXPLORE -> CategoryVisual(
            label = R.string.category_explore,
            subtitle = R.string.category_explore_subtitle,
            icon = R.drawable.ic_cat_explore,
            tint = ExploreTint,
        )
    }
