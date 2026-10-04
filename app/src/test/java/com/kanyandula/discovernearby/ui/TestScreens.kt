package com.kanyandula.discovernearby.ui

import com.kanyandula.discovernearby.DiscoverApplication
import org.robolectric.RuntimeEnvironment

/** Robolectric qualifiers for the reference AVD's automotive_1024p_landscape screen. */
const val AUTOMOTIVE_1024P = "w1024dp-h768dp-land-mdpi"

/** The app's own container, as MainActivity passes it (Robolectric creates DiscoverApplication). */
fun appContainer() = (RuntimeEnvironment.getApplication() as DiscoverApplication).container
