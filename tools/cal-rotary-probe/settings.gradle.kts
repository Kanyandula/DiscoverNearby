// Throwaway Car App Library rotary probe (DN-SP-002). A standalone build: the root build, :app and CI never see it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "cal-rotary-probe"
