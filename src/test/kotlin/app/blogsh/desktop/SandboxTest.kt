package app.blogsh.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The whole way, against a real blog: connected by a code its
 * `./blog.sh pair` gave, then its first screen read and drawn. Runs only
 * where it is given a code -- `BLOGSH_PAIR_CODE` -- and the blog behind
 * it (the Android app's practice server does: a copy of the engine and a
 * sshd of its own, on this machine). With `BLOGSH_SHOTS`, the screen is
 * drawn into that directory too.
 */
class SandboxTest {
    @Test
    fun aBlogIsConnectedByItsCodeAndItsFirstScreenRead() {
        val text = System.getenv("BLOGSH_PAIR_CODE").orEmpty()
        assumeTrue("no BLOGSH_PAIR_CODE", text.isNotBlank())
        // A home of its own, gone afterwards with the key made in it.
        val home = Files.createTempDirectory("blogsh-desktop").toFile().apply { deleteOnExit() }
        Runtime.getRuntime().addShutdownHook(Thread { home.deleteRecursively() })
        System.setProperty("blogsh.home", home.path)
        Machine.install()
        Engine.installCrypto()

        val code = Connect.read(text).getOrThrow()
        runBlocking { Connect.byCode(code) }
        assertNotNull(Blogs.current)
        assertEquals(true, File(home, "keys").listFiles()?.isNotEmpty())

        val state = GlanceState()
        runBlocking { state.load() }
        assertNull(state.problem, state.problem)
        assertNotNull(state.identity)
        println("blog: ${state.identity?.site?.name} · engine ${state.identity?.engine} · queue ${state.queue?.queue?.size} · drafts ${state.drafts?.posts?.size}")

        System.getenv("BLOGSH_SHOTS")?.takeIf { it.isNotBlank() }?.let { directory ->
            File(directory).mkdirs()
            // The window as it opens: the first screen reads the blog by itself, over the network.
            ImageComposeScene(1280, 800, Density(1f)) { App() }.use { scene ->
                var time = 0L
                repeat(60) {
                    scene.render(time)
                    time += 50_000_000L
                    Thread.sleep(50)
                }
                File(directory, "home.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
