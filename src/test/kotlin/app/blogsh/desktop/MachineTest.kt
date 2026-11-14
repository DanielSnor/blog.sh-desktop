package app.blogsh.desktop

import app.blogsh.android.model.Word
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class MachineTest {
    private val made = mutableListOf<File>()

    private fun scratch(): File = Files.createTempDirectory("blogsh-desktop-test").toFile().also { made += it }

    @After
    fun tidy() {
        made.forEach { it.deleteRecursively() }
    }

    /** The Android app's files say a word the way Android reads them: quotes, apostrophes, lines, entities. */
    @Test
    fun aWordIsReadAsAndroidReadsIt() {
        val xml = """
            <resources>
                <string name="plain">Connect</string>
                <string name="escaped">Don\'t \"wait\"\nthen &amp; now</string>
                <string name="quoted">"  spaced  "</string>
                <string name="formatted" formatted="true">Connected: %1${'$'}s</string>
            </resources>
        """.trimIndent()
        val words = Words.parse(xml)
        assertEquals("Connect", words["plain"])
        assertEquals("Don't \"wait\"\nthen & now", words["escaped"])
        assertEquals("  spaced  ", words["quoted"])
        assertEquals("Connected: %1\$s", words["formatted"])
    }

    /** Every word the model says is in the words the app carries: the build put them there. */
    @Test
    fun everyWordOfTheModelIsCarried() {
        val english = Words::class.java.getResourceAsStream("/words/values/strings.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) } +
            (Words::class.java.getResourceAsStream("/words/values/android.xml")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        val names = Words.parse(english).keys
        assertEquals(emptyList<String>(), Word.entries.map { it.name }.filter { it !in names })
    }

    /** What was written is there the next time the app starts, and a value taken away is gone. */
    @Test
    fun settingsOutliveTheApp() {
        val file = File(scratch(), "settings.properties")
        FileNotes(file).apply {
            write("blogs", "[{\"name\":\"Můj blog\"}]")
            write("language", "cs")
            write("language", null)
        }
        val again = FileNotes(file)
        assertEquals("[{\"name\":\"Můj blog\"}]", again.read("blogs"))
        assertNull(again.read("language"))
        assertEquals(setOf("blogs"), again.keys())
        assertFalse(File(file.path + ".new").exists())
    }

    /** A key outside Windows is a file only its owner reads, and it comes back as it was written. */
    @Test
    fun aKeyIsKeptForItsOwnerAlone() {
        assumeFalse(Machine.isWindows)
        val directory = File(scratch(), "keys")
        val vault = OwnerOnlyVault(directory)
        val seed = ByteArray(32) { it.toByte() }
        assertFalse(vault.has("blog-a"))
        vault.write("blog-a", seed)
        assertTrue(vault.has("blog-a"))
        assertArrayEquals(seed, vault.read("blog-a"))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(File(directory, "blog-a.key").toPath())))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.toPath())))
        vault.delete("blog-a")
        assertNull(vault.read("blog-a"))
    }

    /** On Windows the system seals the key: what lies in the file is not the seed, and it opens again. */
    @Test
    fun aKeyOnWindowsIsSealed() {
        org.junit.Assume.assumeTrue(Machine.isWindows)
        val directory = File(scratch(), "keys")
        val vault = SealedVault(directory)
        val seed = ByteArray(32) { (it * 7).toByte() }
        vault.write("blog-a", seed)
        assertFalse(File(directory, "blog-a.bin").readBytes().contentEquals(seed))
        assertArrayEquals(seed, vault.read("blog-a"))
    }

    /** A card's picture is made small, its sides kept in proportion; what is no picture gives none. */
    @Test
    fun aPictureIsMadeSmallForItsCard() {
        val surface = Surface.makeRasterN32Premul(1200, 600)
        surface.canvas.clear(0xFF3366CC.toInt())
        val original = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        val small = Image.makeFromEncoded(Machine.shrink(original, 480, 75)!!)
        assertEquals(480, small.width)
        assertEquals(240, small.height)
        assertNull(Machine.shrink("not a picture".toByteArray(), 480, 75))
    }
}
