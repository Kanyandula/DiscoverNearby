package com.kanyandula.discovernearby

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

open class DiscoverApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** The wiring point (docs/03 §3) with the dev-only provider keys; Robolectric's application builds it keyless. */
    protected open fun createContainer() =
        AppContainer(this, BuildConfig.HERE_API_KEY, BuildConfig.TRIPADVISOR_API_KEY)

    // Place photos (DN-UX-004) are kept in memory only: no disk cache, as nothing is stored (docs/05 §2a), and
    // Tripadvisor's caching policy allows no copy of its photos.
    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context).diskCache(null).build()
}
