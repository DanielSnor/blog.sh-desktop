package app.blogsh.desktop

import app.blogsh.android.model.AppLanguage
import app.blogsh.android.model.BuildStamp
import app.blogsh.android.model.Device
import app.blogsh.android.model.KeyVault
import app.blogsh.android.model.Notes
import app.blogsh.android.model.Spoken
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale
import java.util.Properties
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the model asks of the device (`Device`), as a desktop answers it:
 * a directory of the app's own where each system keeps such things, a
 * file of settings in it, the keys sealed by the system where it can
 * seal them, the words of the Android app.
 */
object Machine {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

    /**
     * Where the app's files go: `%APPDATA%\blogsh` on Windows, Application
     * Support on a Mac, `$XDG_DATA_HOME/blogsh` elsewhere -- or wherever
     * `-Dblogsh.home` says, for a try that is to leave the app's own alone.
     */
    val home: File by lazy {
        val user = File(System.getProperty("user.home"))
        when {
            !System.getProperty("blogsh.home").isNullOrBlank() -> File(System.getProperty("blogsh.home"))
            isWindows -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: File(user, "AppData/Roaming").path, "blogsh")
            isMac -> File(user, "Library/Application Support/blogsh")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: File(user, ".local/share").path, "blogsh")
        }.apply { mkdirs() }
    }

    const val RELEASES = "https://api.github.com/repos/DanielSnor/blog.sh-desktop/releases/latest"

    /** Said once, as the app starts, before anything asks the model. */
    fun install() {
        val notes = FileNotes(File(home, "settings.properties"))
        Device.files = home
        Device.notes = notes
        Device.vault = if (isWindows) SealedVault(File(home, "keys")) else OwnerOnlyVault(File(home, "keys"))
        val language = AppLanguage.read(notes).locale
        Device.language = { language ?: Locale.getDefault() }
        Device.thumbnail = { shrink(it, 480, 75) }
        Device.stamp = {
            runCatching { BuildStamp.of(Machine::class.java.getResourceAsStream("/BuildStamp.txt")!!.use { it.readBytes().toString(Charsets.UTF_8) }) }
                .getOrDefault(BuildStamp(null, null))
                .copy(version = Machine::class.java.`package`?.implementationVersion)
        }
        // A newer version is one of this app's own releases, not the Android app's.
        Device.releases = RELEASES
        Device.log = { what, error -> System.err.println("blogsh: $what: $error") }
        Spoken.source = { word, args -> Words.say(word, args) }
    }

    /**
     * The picture with its long edge no longer than `edge`, as JPEG; null
     * where it is no picture Skia reads.
     */
    fun shrink(original: ByteArray, edge: Int, quality: Int): ByteArray? = runCatching {
        val image = Image.makeFromEncoded(original)
        val long = max(image.width, image.height)
        val scale = if (long > edge) edge.toDouble() / long else 1.0
        val width = max(1, (image.width * scale).roundToInt())
        val height = max(1, (image.height * scale).roundToInt())
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()))
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    }.getOrNull()
}

/**
 * The settings in a file of their own, `key=value` a line: read once,
 * written whole at each change -- into a file beside it first, then put
 * in its place, so that a crash in the middle leaves the old one.
 */
class FileNotes(private val file: File) : Notes {
    private val held = Properties().apply {
        runCatching { file.inputStream().use { load(it.reader(Charsets.UTF_8)) } }
    }

    @Synchronized
    override fun read(key: String): String? = held.getProperty(key)

    @Synchronized
    override fun keys(): Set<String> = held.stringPropertyNames()

    @Synchronized
    override fun write(key: String, value: String?) {
        if (value == null) held.remove(key) else held.setProperty(key, value)
        file.parentFile?.mkdirs()
        val next = File(file.path + ".new")
        next.writer(Charsets.UTF_8).use { held.store(it, null) }
        Files.move(next.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/**
 * The keys on Windows: each seed sealed by the system (DPAPI) to the
 * user who is signed in, in a file of the app's own. Another user of
 * the same computer, or a copy of the file taken elsewhere, opens
 * nothing.
 */
class SealedVault(private val directory: File) : KeyVault {
    private fun file(account: String) = File(directory.apply { mkdirs() }, "${File(account).name}.bin")

    /** Said with the seal, so that a sealed seed of one blog is no other blog's. */
    private fun entropy(account: String) = "blogsh:$account".toByteArray(Charsets.UTF_8)

    override fun has(account: String): Boolean = file(account).exists()

    override fun read(account: String): ByteArray? {
        val file = file(account)
        if (!file.exists()) return null
        return Crypt32Util.cryptUnprotectData(file.readBytes(), entropy(account), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
    }

    override fun write(account: String, seed: ByteArray) {
        file(account).writeBytes(Crypt32Util.cryptProtectData(seed, entropy(account), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, "blog.sh", null))
    }

    override fun delete(account: String) {
        file(account).delete()
    }
}

/**
 * The keys where the system has no seal the app uses yet (a Mac, Linux):
 * a file only its owner may read, as `ssh-keygen` leaves a key without a
 * passphrase. A Mac runs the iOS app as it is; this is what the desktop
 * app is tried out with there.
 */
class OwnerOnlyVault(private val directory: File) : KeyVault {
    private fun file(account: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
            runCatching { Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwx------")) }
        }
        return File(directory, "${File(account).name}.key")
    }

    override fun has(account: String): Boolean = file(account).exists()

    override fun read(account: String): ByteArray? = file(account).takeIf { it.exists() }?.readBytes()

    override fun write(account: String, seed: ByteArray) {
        val file = file(account)
        file.writeBytes(ByteArray(0))
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        file.writeBytes(seed)
    }

    override fun delete(account: String) {
        file(account).delete()
    }
}
