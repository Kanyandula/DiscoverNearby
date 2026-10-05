package com.kanyandula.calprobe

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

private const val TAG = "ProbeNav"
private val CATEGORIES = listOf("Coffee", "Food", "Outdoors", "Family", "Scenic", "Explore")

/** Throwaway rotary probe (DN-SP-002): the Discover journey as Car App Library templates, static data only. */
class ProbeService : CarAppService() {
    // Debug probe only: any host may bind. A real app validates hosts.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = GridScreen(carContext)
    }
}

private class GridScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        CATEGORIES.forEach { category ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(category)
                    .setImage(CarIcon.APP_ICON)
                    .setOnClickListener { screenManager.push(ListScreen(carContext, category)) }
                    .build(),
            )
        }
        return GridTemplate.Builder()
            .setTitle("Discover Nearby (probe)")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }
}

private class ListScreen(carContext: CarContext, private val category: String) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val rows = ItemList.Builder()
        (1..5).forEach { n ->
            rows.addItem(
                Row.Builder()
                    .setTitle("$category place $n")
                    .addText("${n * 0.4} km")
                    .setOnClickListener { screenManager.push(DetailsScreen(carContext, "$category place $n")) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle(category)
            .setHeaderAction(Action.BACK)
            .setSingleList(rows.build())
            .build()
    }
}

private class DetailsScreen(carContext: CarContext, private val name: String) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val navigate = Action.Builder()
            .setTitle("Navigate")
            .setOnClickListener {
                Log.i(TAG, "navigate selected")
                // Informational: the template-world hand-off. The stub only handles ACTION_VIEW geo:.
                runCatching {
                    carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=probe")))
                }.onSuccess { Log.i(TAG, "startCarApp returned") }
                    .onFailure { Log.i(TAG, "startCarApp failed: ${it.javaClass.simpleName}") }
            }
            .build()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("1.2 km away").build())
            .addRow(Row.Builder().setTitle("4.5 ★ (120 reviews)").build())
            .addAction(navigate)
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle(name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
