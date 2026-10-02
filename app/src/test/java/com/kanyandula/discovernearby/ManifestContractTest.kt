package com.kanyandula.discovernearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Pins the manifest contract from docs/03 §6. Local unit tests run with the module
 * directory as the working directory, so the source manifest is read directly.
 */
class ManifestContractTest {

    private val manifest = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun Element.androidAttr(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).elements()

    @Test
    fun launcherActivityIsDistractionOptimized() {
        val launchers = elements("activity").filter { activity ->
            activity.getElementsByTagName("category").elements()
                .any { it.androidAttr("name") == "android.intent.category.LAUNCHER" }
        }
        assertEquals("exactly one launcher activity", 1, launchers.size)
        assertEquals(".ui.MainActivity", launchers.single().androidAttr("name"))

        val optimized = launchers.single().getElementsByTagName("meta-data").elements().any {
            it.androidAttr("name") == "distractionOptimized" && it.androidAttr("value") == "true"
        }
        assertTrue("launcher activity must declare distractionOptimized=true", optimized)
    }

    @Test
    fun requiresAutomotiveHardware() {
        val automotive = elements("uses-feature").single { it.androidAttr("name") == "android.hardware.type.automotive" }
        assertEquals("true", automotive.androidAttr("required"))
    }

    @Test
    fun declaresLocationAndInternetPermissions() {
        val permissions = elements("uses-permission").map { it.androidAttr("name") }.toSet()
        assertTrue(permissions.containsAll(setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET")))
    }

    @Test
    fun declaresNoCarAppLibraryComponents() {
        val names = (elements("service") + elements("meta-data") + elements("uses-feature") + elements("action"))
            .map { it.androidAttr("name") }
        assertFalse(names.any { it.startsWith("androidx.car.app") || it == "android.software.car.templates_host" })
    }
}
