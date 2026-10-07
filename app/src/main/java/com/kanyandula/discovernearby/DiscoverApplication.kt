package com.kanyandula.discovernearby

import android.app.Application

open class DiscoverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** The wiring point (docs/03 §3) with the dev-only HERE key; Robolectric's application builds it keyless. */
    protected open fun createContainer() = AppContainer(this, BuildConfig.HERE_API_KEY)
}
