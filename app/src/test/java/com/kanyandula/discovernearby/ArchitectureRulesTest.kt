package com.kanyandula.discovernearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * docs/03 §2–§3: only ui/ imports Compose (any androidx.*.compose package); only
 * car/CarDrivingRestrictions imports android.car. A file's place comes from its package
 * line, so every non-test source set (src/main/java, src/main/kotlin, src/debug, …) is
 * covered. Local unit tests run with the module directory as the working directory.
 */
class ArchitectureRulesTest {

    private val base = "com.kanyandula.discovernearby"
    private val testSourceSets = setOf("test", "androidTest", "testFixtures")
    private val composeImport = Regex("""^import androidx\.([\w.]+\.)?compose\.""")
    private val carImport = Regex("""^import android\.car\.""")
    private val hereImport = Regex("""^import com\.kanyandula\.discovernearby\.places\.here\.""")

    private class Source(val file: File, val pkg: String, val imports: List<String>)

    private val sources: List<Source> by lazy {
        val src = File("src")
        assertTrue("source root ${src.absolutePath} not found", src.isDirectory)
        src.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in testSourceSets }
            .flatMap { set -> set.walkTopDown().filter { it.extension == "kt" }.toList() }
            .map { file ->
                val lines = file.readLines()
                val pkg = lines.firstOrNull { it.startsWith("package ") }?.removePrefix("package ")?.trim().orEmpty()
                Source(file, pkg, lines.filter { it.startsWith("import ") })
            }
    }

    private fun inPackage(source: Source, sub: String) =
        source.pkg == "$base.$sub" || source.pkg.startsWith("$base.$sub.")

    private fun violations(forbidden: Regex, allowed: (Source) -> Boolean): List<String> =
        sources.filterNot(allowed).flatMap { source ->
            source.imports.filter { forbidden.containsMatchIn(it) }.map { "${source.file.path}: $it" }
        }

    @Test
    fun scansMainSources() {
        assertTrue("no main sources scanned", sources.any { it.pkg.startsWith(base) })
    }

    @Test
    fun onlyUiImportsCompose() {
        assertEquals(emptyList<String>(), violations(composeImport) { inPackage(it, "ui") })
    }

    @Test
    fun onlyCarDrivingRestrictionsImportsAndroidCar() {
        val allowed = { s: Source -> s.pkg == "$base.car" && s.file.name == "CarDrivingRestrictions.kt" }
        assertEquals(emptyList<String>(), violations(carImport, allowed))
    }

    // docs/03 §8: provider response models never leave the provider package; only the wiring point may name it.
    @Test
    fun onlyAppContainerReachesIntoTheHerePackage() {
        val allowed = { s: Source -> inPackage(s, "places.here") || s.file.name == "AppContainer.kt" }
        assertEquals(emptyList<String>(), violations(hereImport, allowed))
    }
}
