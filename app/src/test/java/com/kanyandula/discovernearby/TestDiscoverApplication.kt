package com.kanyandula.discovernearby

/**
 * Robolectric's application: by its convention, a class named "Test" + the manifest's application class, in the same
 * package, replaces it in every test. Its container has no key, so every test serves the fakes and none reaches live
 * HERE, whatever local.properties holds. Matches CI, which has no key.
 */
class TestDiscoverApplication : DiscoverApplication() {
    override fun createContainer() = AppContainer(this, hereApiKey = "")
}
