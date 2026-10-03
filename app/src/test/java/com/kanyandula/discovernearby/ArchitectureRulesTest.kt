package com.kanyandula.discovernearby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * docs/03 §2: only ui/ imports Compose; only car/ imports android.car. Local unit tests
 * run with the module directory as the working directory.
 */
class ArchitectureRulesTest {

    private val root = File("src/main/java/com/kanyandula/discovernearby")

    private fun violations(forbiddenImport: String, allowedPackage: String): List<String> =
        root.walkTopDown()
            .filter { it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.startsWith("$allowedPackage/") }
            .flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import $forbiddenImport") }
                    .map { "${file.relativeTo(root)}: $it" }
            }
            .toList()

    @Test
    fun onlyUiImportsCompose() {
        assertEquals(emptyList<String>(), violations("androidx.compose.", allowedPackage = "ui"))
    }

    @Test
    fun onlyCarImportsAndroidCar() {
        assertEquals(emptyList<String>(), violations("android.car.", allowedPackage = "car"))
    }
}
