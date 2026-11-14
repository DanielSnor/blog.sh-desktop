import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(desk.plugins.compose)
}

// A working copy may live in a folder something syncs (iCloud, Dropbox), and
// a build writes thousands of files that have no business there. A line
// `build.root=/some/dir` in local.properties puts the output under that
// directory instead of beside the sources.
val buildRoot: String? = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }.getProperty("build.root")
}
if (buildRoot != null) layout.buildDirectory.set(file("$buildRoot/${project.name}"))

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("app.blogsh:core")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(desk.jna.platform)

    testImplementation(libs.junit)
}

// The app's words are the Android app's: the same files, read as they are
// written (`values/strings.xml`, `values-cs/…`), carried in the app under
// `words/`.
val android = rootDir.resolve("../blog.sh-android/app/src/main/res")
tasks.processResources {
    from(android) {
        include("values*/strings.xml", "values*/android*.xml")
        into("words")
    }
}

compose.desktop {
    application {
        mainClass = "app.blogsh.desktop.MainKt"
        nativeDistributions {
            // A Mac runs the iOS app as it is: Windows, and Linux beside it.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "blogsh"
            packageVersion = "0.1.0"
            description = "./blog.sh --anywhere"
            vendor = "Daniel Šnor"
            windows {
                menuGroup = "blog.sh"
                // One for the app's whole life: by it Windows knows a newer installer replaces the older one.
                upgradeUuid = "6AB4610D-BAA0-4DA4-AF37-E7C1873C672E"
            }
        }
    }
}
