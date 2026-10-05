package com.kanyandula.stubnavigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Pins docs/03 §11: one activity that any app's ACTION_VIEW geo: intent resolves to, shown while driving.
 * Local unit tests run in the module directory, so the source manifest is read directly.
 */
class StubManifestContractTest {

    private companion object {
        // Parsed once per class; JUnit creates a new instance for every test.
        val activity: Element by lazy {
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File("src/main/AndroidManifest.xml"))
                .getElementsByTagName("activity").elements().single()
        }
    }

    @Test
    fun oneExportedActivityHandlesGeoViewIntents() {
        assertEquals(".StubNavigationActivity", activity.androidAttr("name"))
        // Reachable from another app's implicit intent; the app never names a navigation package.
        assertEquals("true", activity.androidAttr("exported"))
        val filter = activity.getElementsByTagName("intent-filter").elements().single()
        assertEquals(listOf("android.intent.action.VIEW"), filter.named("action"))
        // startActivity resolves implicit intents only to activities in the DEFAULT category.
        assertEquals(listOf("android.intent.category.DEFAULT"), filter.named("category"))
        assertEquals(listOf("geo"), filter.getElementsByTagName("data").elements().map { it.androidAttr("scheme") })
    }

    @Test
    fun isDistractionOptimized() {
        val optimized = activity.getElementsByTagName("meta-data").elements().any {
            it.androidAttr("name") == "distractionOptimized" && it.androidAttr("value") == "true"
        }
        assertTrue("the stub must show while driving", optimized)
    }

    // A later hand-off must reach the open stub (onNewIntent), not just bring the old screen forward.
    @Test
    fun keepsOneInstance() {
        assertEquals("singleTask", activity.androidAttr("launchMode"))
    }
}

private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)

private fun Element.named(tag: String): List<String> =
    getElementsByTagName(tag).elements().map { it.androidAttr("name") }
