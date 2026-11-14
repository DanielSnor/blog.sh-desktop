package app.blogsh.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.BuildStamp
import app.blogsh.android.model.Colouring
import app.blogsh.android.model.DayNight
import app.blogsh.android.model.Device
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Now
import app.blogsh.android.model.Reach
import app.blogsh.android.model.Reading
import app.blogsh.android.model.TextSize
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.Unsent
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.MenuEntry
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost
import app.blogsh.android.ui.screens.AddBlogSheet
import app.blogsh.android.ui.screens.ArchiveScreen
import app.blogsh.android.ui.screens.BlogsSheet
import app.blogsh.android.ui.screens.ComposeScreen
import app.blogsh.android.ui.screens.Diagnosis
import app.blogsh.android.ui.screens.DiagnosisScreen
import app.blogsh.android.ui.screens.PostPickerScreen
import app.blogsh.android.ui.screens.PropsScreen
import app.blogsh.android.ui.screens.QueueScreen
import app.blogsh.android.ui.screens.SettingsSheet
import app.blogsh.android.ui.screens.SiteScreen
import app.blogsh.android.ui.screens.TextEditScreen
import app.blogsh.android.ui.screens.TrashScreen
import org.jetbrains.skia.EncodedImageFormat
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Every screen of the window, drawn as the Android app's tablet on its
 * side draws it -- from the answers the Android app's own pictures are
 * drawn from (its test fixtures), with the clock standing still. Each one
 * is written as a picture (`build/screens/<look>/<screen>.png`) to be
 * looked at; a screen that cannot be drawn on a desktop fails here.
 */
class ScreensTest {
    private class Look(val name: String, val lang: String, val night: Boolean)

    companion object {
        private val noon: Instant = Instant.parse("2026-10-08T10:00:00Z")
        private const val BLOG = "shots"
        private val looks = listOf(Look("cs", "cs", night = false), Look("en-night", "en", night = true))
        private val home: File = Files.createTempDirectory("blogsh-screens").toFile()
        private val zone = TimeZone.getDefault()
        private val locale = Locale.getDefault()

        @JvmStatic
        @BeforeClass
        fun dress() {
            System.setProperty("blogsh.home", home.path)
            Machine.install()
            Device.language = { Locale.getDefault() }
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
            Now.clock = { noon }
            BuildStamp.own = BuildStamp("9e1de85", Instant.parse("2026-10-08T13:31:11Z").toEpochMilli(), "0.1.0")
            Engine.stand = ::answer
            for (blog in Blogs.all) Blogs.remove(blog.id)
            Blogs.adopt(Blog(id = BLOG, host = "blog.invalid", port = 2222, user = "me", name = "blog.sh"))
            Reach.shared.heard(Reach.server("blog.invalid", 2222))
            Reading.shared.wear(Colouring.Blog)
            Reading.shared.choose(null)
            Reading.shared.set(TextSize.System)
        }

        @JvmStatic
        @AfterClass
        fun undress() {
            Engine.stand = null
            Now.clock = { Instant.now() }
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
            home.deleteRecursively()
        }

        /** What the engine would answer: the answers the Android app's pictures are drawn from. */
        private fun answer(args: List<String>): ByteArray {
            val name = when {
                args == listOf("list", "--drafts") -> "list-drafts"
                args.firstOrNull() == "empty" -> "empty-" + args.getOrElse(1) { "" }
                else -> args.firstOrNull() ?: ""
            }
            return checkNotNull(ScreensTest::class.java.classLoader?.getResourceAsStream("fixtures/shots/$name.json")) { "no answer kept for $args" }.use { it.readBytes() }
        }
    }

    /** The window with `screen` open beside the menu and `over` laid on top, drawn in every look. */
    private fun shot(name: String, tag: MenuEntry? = null, over: (@Composable () -> Unit)? = null, screen: (@Composable () -> Unit)? = null) {
        val out = File(System.getProperty("blogsh.screens") ?: "build/screens")
        for (look in looks) {
            Locale.setDefault(Locale.forLanguageTag(look.lang))
            Reading.shared.hold(if (look.night) DayNight.Night else DayNight.System)
            ImageComposeScene(1280, 800, Density(1f)) {
                BlogshTheme {
                    val nav = remember { Nav() }
                    val home = remember { HomeState() }
                    CompositionLocalProvider(LocalNav provides nav) {
                        NavHost(nav) { HomeScreen(home) }
                        if (screen != null) LaunchedEffect(Unit) { nav.open(tag, screen) }
                        over?.invoke()
                    }
                }
            }.use { scene ->
                // The answers come at once, the drawing a frame or two later.
                var time = 0L
                repeat(30) {
                    scene.render(time)
                    time += 50_000_000L
                    Thread.sleep(20)
                }
                val image = scene.render(time)
                val file = File(out, "${look.name}/$name.png").apply { parentFile.mkdirs() }
                file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                assertTrue(file.length() > 0)
            }
        }
    }

    @Test fun home() = shot("home")

    @Test fun begun() {
        Unsent("Pes v trávě", "pes, zahrada", "Plazí se.\n\n![Pes](photo-1.jpg)\n", noon.minusSeconds(3 * 3600).toEpochMilli()).keep(BLOG, BlogShelf.notes)
        Unsaved("Deset kilometrů kolem rybníka. A jedenáctý navíc.", "abc", noon.minusSeconds(1800).toEpochMilli(), "Doběhnuto")
            .keep(BLOG, "dobehnuto", Unsaved.What.Text, BlogShelf.notes)
        try {
            shot("home-begun")
        } finally {
            Unsent.forget(BLOG, BlogShelf.notes)
            Unsaved.forgetAll(BLOG, BlogShelf.notes)
        }
    }

    @Test fun archive() = shot("archive", MenuEntry.Browse) { ArchiveScreen() }

    @Test fun queue() = shot("queue", MenuEntry.Queue) { QueueScreen() }

    @Test fun posts() = shot("posts", MenuEntry.Post) { PostPickerScreen() }

    @Test fun properties() = shot("properties", MenuEntry.Post) { PropsScreen("dobehnuto") }

    @Test fun text() = shot("text", MenuEntry.Post) { TextEditScreen("dobehnuto") }

    @Test fun newPost() = shot("new-post", MenuEntry.Add) { ComposeScreen() }

    @Test fun site() = shot("site", MenuEntry.Rebuild) { SiteScreen() }

    @Test fun installationCheck() = shot("doctor", MenuEntry.Rebuild) { DiagnosisScreen(Diagnosis.Installation) }

    @Test fun trash() = shot("trash", MenuEntry.Restore) { TrashScreen() }

    @Test fun settings() = shot("settings", over = { SettingsSheet(onDismiss = {}) })

    @Test fun blogs() = shot("blogs", over = { BlogsSheet(onDismiss = {}) })

    @Test fun addBlog() = shot("add-blog", over = { AddBlogSheet(onBack = {}, onDone = {}) })
}
