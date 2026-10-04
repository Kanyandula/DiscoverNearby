package com.kanyandula.discovernearby

import android.app.Application

class DiscoverApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
