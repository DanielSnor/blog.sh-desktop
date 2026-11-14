pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        // The versions the Android app is built with, Kotlin first among them --
        // copied with the model and the screens (shared/), which are built with them.
        create("libs") { from(files("shared/versions.toml")) }
        create("desk") { from(files("gradle/desktop.versions.toml")) }
    }
}

rootProject.name = "blogsh-desktop"
