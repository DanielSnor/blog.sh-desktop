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
        // The versions the Android app is built with, Kotlin first among them:
        // the model is one for both apps and is built with them.
        create("libs") { from(files("../blog.sh-android/gradle/libs.versions.toml")) }
        create("desk") { from(files("gradle/desktop.versions.toml")) }
    }
}

rootProject.name = "blogsh-desktop"

// The model, from the Android app's working copy beside this one.
includeBuild("../blog.sh-android/core")
