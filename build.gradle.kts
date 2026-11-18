import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(desk.plugins.compose)
}

// The same number for the same state as the iOS and the Android app: this one
// has what the Android app 0.8.1 has, in a desktop's own ways.
version = "0.8.1"

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

// The system the app is built for: this one, or another named by
// `-Pblogsh.target` -- linux-x64, linux-arm64, windows-x64, windows-arm64. The code is
// the same everywhere; only Skia's own library differs, and the
// installer, which is made by that system's Java (`jpackage`) on it.
val target = providers.gradleProperty("blogsh.target").orNull
val skia: String = with(org.jetbrains.compose.ComposePlugin.DesktopDependencies) {
    when (target) {
        null -> currentOs
        "linux-x64" -> linux_x64
        "linux-arm64" -> linux_arm64
        "windows-x64" -> windows_x64
        "windows-arm64" -> windows_arm64
        else -> error("blogsh.target: linux-x64, linux-arm64, windows-x64 or windows-arm64, not $target")
    }
}

dependencies {
    implementation(skia)
    // What the model (shared/) is built on, as the Android app builds it.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sshj)
    implementation(libs.bouncycastle)
    implementation(libs.zxing.core)
    implementation(compose.material3)
    implementation(desk.jna.platform)
    // What the Android app's screens use besides Compose, for the desktop.
    implementation(desk.lifecycle.runtime.compose)
    implementation(libs.reorderable)
    implementation(desk.icu4j)

    testImplementation(libs.junit)
}

// The model, the screens, the words, marks and faces of type are the
// Android app's, copied into shared/ by tools/sync-android and never edited
// here; what only a desktop has is in src/.
val shared = layout.projectDirectory.dir("shared")

/**
 * The ids the screens name the Android app's resources by (`R.string.…`,
 * `R.drawable.…`, `R.font.…`), and behind each id the name it stands for:
 * read from its res/ at every build.
 */
abstract class ResourceIdsTask : DefaultTask() {
    @get:InputDirectory
    abstract val res: DirectoryProperty

    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun write() {
        val res = res.get().asFile
        val strings = res.resolve("values").listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }
            .flatMap { Regex("""<string name="([^"]+)"""").findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }.distinct()
        val drawables = listOf("drawable", "drawable-nodpi").flatMap { dir -> res.resolve(dir).listFiles().orEmpty().map { "$dir/${it.name}" } }.sorted()
        val fonts = res.resolve("font").listFiles().orEmpty().map { "font/${it.name}" }.sorted()
        fun block(kind: String, names: List<String>, base: Int) = names.mapIndexed { i, name -> "        const val `$name` = ${base + i}" }
            .joinToString("\n", "    object $kind {\n", "\n    }\n")
        fun bare(path: String) = path.substringAfterLast("/").substringBeforeLast(".")
        fun array(names: List<String>) = names.joinToString(", ", "arrayOf(", ")") { "\"$it\"" }
        val text = buildString {
            append("package app.blogsh.android\n\n")
            append("// Made by the build from the Android app's res/ (ResourceIdsTask); not written by hand.\n\n")
            append("object R {\n")
            append(block("string", strings, 0x7f010000)).append(block("drawable", drawables.map(::bare), 0x7f020000)).append(block("font", fonts.map(::bare), 0x7f030000))
            append("}\n\n")
            append("object ResourceNames {\n")
            append("    const val VERSION = \"${version.get()}\"\n")
            append("    val strings = ${array(strings)}\n")
            append("    val drawables = ${array(drawables)}\n")
            append("    val fonts = ${array(fonts)}\n")
            append("}\n")
        }
        output.get().asFile.resolve("app/blogsh/android").apply { mkdirs() }.resolve("R.kt").writeText(text)
    }
}

val resourceIds = tasks.register<ResourceIdsTask>("resourceIds") {
    res.set(shared.dir("res"))
    version.set(project.version.toString())
    output.set(layout.buildDirectory.dir("generated/resourceIds"))
}

kotlin.sourceSets["main"].kotlin.srcDir(shared.dir("kotlin"))
sourceSets["main"].resources.srcDir(shared.dir("resources"))
kotlin.sourceSets["main"].kotlin.srcDir(resourceIds)

// The answers the Android app's pictures of its screens are drawn from: the desktop's are drawn from them too.
sourceSets["test"].resources.srcDir(shared.dir("test-resources"))

tasks.test {
    systemProperty("blogsh.screens", layout.buildDirectory.dir("screens").get().asFile.path)
}

// The words, the marks and the faces are the Android app's files, carried in
// as they are written: `words/values/strings.xml`, `res/drawable/…`,
// `res/font/…`. A mark names its colour the way Android does
// (`@android:color/white`), which the desktop reads as a colour of its own.
tasks.processResources {
    from(shared.dir("res")) {
        include("values*/strings.xml", "values*/android*.xml")
        into("words")
    }
    from(shared.dir("res")) {
        include("drawable/*.xml", "drawable-nodpi/*", "font/*")
        filesMatching("drawable/*.xml") { filter { it.replace("@android:color/white", "#FFFFFFFF").replace("@android:color/black", "#FF000000") } }
        into("res")
    }
}

/**
 * The app and every library it runs on, as jars in one directory: what
 * the target system's `jpackage` makes an installer of, where this
 * build cannot run it itself.
 */
val appJars = tasks.register<Sync>("appJars") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    // Two libraries may share a file name (androidx and JetBrains publish lifecycle so):
    // each one from Gradle's cache goes under its group as well.
    eachFile {
        val parts = file.path.split(File.separatorChar)
        val at = parts.indexOf("files-2.1")
        if (at >= 0) name = parts[at + 1] + "." + name
    }
    into(layout.buildDirectory.dir("app-jars"))
}

/**
 * The parts of Java the installed app carries: what its libraries ask
 * for (jdeps over every jar: SSH needs naming and GSS, the screens the
 * desktop), and what nothing names but the app needs all the same -- the
 * languages' dates and numbers (localedata), the code pages an older
 * text file is written in (charsets), the curves a server's key may be on
 * (crypto.ec), a screen reader's way in (accessibility). Without them the
 * app ran from the build and failed installed.
 */
val runtimeModules = listOf(
    "java.base", "java.desktop", "java.instrument", "java.logging", "java.naming", "java.prefs",
    "java.security.jgss", "java.sql", "java.xml", "jdk.unsupported",
    "jdk.localedata", "jdk.charsets", "jdk.crypto.ec", "jdk.accessibility",
)

tasks.register("printRuntimeModules") {
    val modules = runtimeModules.joinToString(",")
    doLast { println(modules) }
}

compose.desktop {
    application {
        mainClass = "app.blogsh.desktop.MainKt"
        nativeDistributions {
            // A Mac runs the iOS app as it is: Windows, and Linux beside it.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            modules(*runtimeModules.toTypedArray())
            packageName = "blogsh"
            packageVersion = project.version.toString()
            description = "./blog.sh --anywhere"
            vendor = "Daniel Šnor"
            linux {
                // Where a desktop's menu files the app: among the office programs, as on Windows.
                menuGroup = "Office"
            }
            windows {
                menuGroup = "blog.sh"
                // One for the app's whole life: by it Windows knows a newer installer replaces the older one.
                upgradeUuid = "6AB4610D-BAA0-4DA4-AF37-E7C1873C672E"
            }
        }
    }
}
