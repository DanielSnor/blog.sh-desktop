import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(desk.plugins.compose)
}

version = "0.1.0"

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
    // What the Android app's screens use besides Compose, for the desktop.
    implementation(desk.lifecycle.runtime.compose)
    implementation(libs.reorderable)
    implementation(desk.icu4j)

    testImplementation(libs.junit)
}

// The screens are the Android app's own: compiled here as they are written
// there, with what only Android has (the camera, the web view, the share
// sheet, the system's answers in OnAndroid.kt) left out -- this app answers
// the same names in its own files (OnDesktop.kt and the rest).
val androidApp = rootDir.resolve("../blog.sh-android/app/src/main")
val shareScreens = tasks.register<Sync>("shareScreens") {
    from(androidApp.resolve("java")) {
        include("app/blogsh/android/ui/**/*.kt", "app/blogsh/android/model/Doing.kt")
        exclude(
            "app/blogsh/android/ui/OnAndroid.kt",
            "app/blogsh/android/ui/Share.kt",
            "app/blogsh/android/ui/screens/CodeScanner.kt",
            "app/blogsh/android/ui/screens/PreviewViews.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/screens"))
}

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
    res.set(androidApp.resolve("res"))
    version.set(project.version.toString())
    output.set(layout.buildDirectory.dir("generated/resourceIds"))
}

kotlin.sourceSets["main"].kotlin.srcDir(shareScreens)
kotlin.sourceSets["main"].kotlin.srcDir(resourceIds)

// The answers the Android app's pictures of its screens are drawn from: the desktop's are drawn from them too.
sourceSets["test"].resources.srcDir(androidApp.resolve("../test/resources"))

tasks.test {
    systemProperty("blogsh.screens", layout.buildDirectory.dir("screens").get().asFile.path)
}

// The words, the marks and the faces are the Android app's files, carried in
// as they are written: `words/values/strings.xml`, `res/drawable/…`,
// `res/font/…`. A mark names its colour the way Android does
// (`@android:color/white`), which the desktop reads as a colour of its own.
tasks.processResources {
    from(androidApp.resolve("res")) {
        include("values*/strings.xml", "values*/android*.xml")
        into("words")
    }
    from(androidApp.resolve("res")) {
        include("drawable/*.xml", "drawable-nodpi/*", "font/*")
        filesMatching("drawable/*.xml") { filter { it.replace("@android:color/white", "#FFFFFFFF").replace("@android:color/black", "#FF000000") } }
        into("res")
    }
}

compose.desktop {
    application {
        mainClass = "app.blogsh.desktop.MainKt"
        nativeDistributions {
            // A Mac runs the iOS app as it is: Windows, and Linux beside it.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "blogsh"
            packageVersion = project.version.toString()
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
