package app.blogsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.blogsh.android.ResourceNames
import app.blogsh.android.model.Device
import app.blogsh.android.model.Kept
import app.blogsh.android.model.NetworkWatch
import app.blogsh.android.model.Pictures
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TextFile
import app.blogsh.desktop.Connect
import app.blogsh.desktop.Machine
import app.blogsh.desktop.Words
import com.ibm.icu.text.DateTimePatternGenerator
import com.ibm.icu.text.MeasureFormat
import com.ibm.icu.util.Measure
import com.ibm.icu.util.MeasureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.xml.sax.InputSource
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// What the screens ask of the system they are drawn on, as a desktop
// answers it. The screens are the Android app's own, compiled here as
// they are written there; the Android app answers these same names in
// ui/OnAndroid.kt. Ids are the ones the build made from the Android
// app's res/ (R and ResourceNames).

private const val KIND = 0xffff0000.toInt()

private fun nameOf(id: Int, names: Array<String>, base: Int): String {
    check(id and KIND == base) { "not one of these resources: $id" }
    return names[id and 0xffff]
}

/** The words under this id, in the app's language. */
@Composable
fun stringResource(id: Int): String = Words.say(nameOf(id, ResourceNames.strings, 0x7f010000))

@Composable
fun stringResource(id: Int, vararg args: Any): String = Words.say(nameOf(id, ResourceNames.strings, 0x7f010000), *args)

/** One of Symbols -- a mark drawn as a vector, as Android draws it -- or a picture. */
@Composable
fun painterResource(id: Int): Painter {
    val path = "res/" + nameOf(id, ResourceNames.drawables, 0x7f020000)
    if (path.endsWith(".xml")) {
        val density = LocalDensity.current
        val vector = remember(path, density) {
            Machine::class.java.getResourceAsStream("/$path")!!.use { loadXmlImageVector(InputSource(it), density) }
        }
        return rememberVectorPainter(vector)
    }
    return remember(path) {
        val bytes = Machine::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
        BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }
}

/** A face of type among the app's resources: one weight of it, or one place on a variable face's axis. */
fun appFont(id: Int, weight: FontWeight, variation: FontVariation.Settings? = null): Font {
    val path = "res/" + nameOf(id, ResourceNames.fonts, 0x7f030000)
    return if (variation == null) androidx.compose.ui.text.platform.Font(path, weight, FontStyle.Normal)
    else androidx.compose.ui.text.platform.Font(path, weight, FontStyle.Normal, variation)
}

/**
 * The system the screens are drawn on, where a screen has to ask it
 * something of its own: a word outside composition, the clipboard, a
 * picture or a file picked.
 */
class Host {
    fun getString(id: Int): String = Words.say(nameOf(id, ResourceNames.strings, 0x7f010000))

    fun getString(id: Int, vararg args: Any): String = Words.say(nameOf(id, ResourceNames.strings, 0x7f010000), *args)
}

private val host = Host()

@Composable
fun rememberHost(): Host = host

/**
 * The way back: the Esc key. Each screen and sheet that has a way back
 * of its own says it here as it comes, and the last one said that is
 * still there and on takes the key -- the one in front.
 */
object BackKeys {
    class Way(val enabled: () -> Boolean, val back: () -> Unit)

    private val ways = mutableStateListOf<Way>()

    fun add(way: Way) = ways.add(way)

    fun remove(way: Way) = ways.remove(way)

    /** Esc pressed: true where something took it. */
    fun back(): Boolean {
        val way = ways.lastOrNull { it.enabled() } ?: return false
        way.back()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val on by rememberUpdatedState(enabled)
    val back by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val way = BackKeys.Way({ on }, { back() })
        BackKeys.add(way)
        onDispose { BackKeys.remove(way) }
    }
}

/** The whole window, its corner the window's corner, whatever asks for it. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

/**
 * A sheet: over the screen and the menu, in the same window, the rest
 * dimmed as a tablet dims it. Esc leaves it, as the way back leaves it
 * on a tablet -- unless the sheet has a way back of its own.
 */
@Composable
fun SheetDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismissRequest)
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = null,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { event -> event.type == KeyEventType.KeyDown && event.key == Key.Escape && BackKeys.back() },
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) { content() }
    }
}

/** A sheet's bars are a phone's: a desktop window has none. */
@Composable
fun SheetBars() {
}

/** A window is a wide display. */
@Composable
fun wideDisplay(): Boolean = true

/** The window's own scale is the system's already; the settings' steps of type are added to it. */
@Composable
fun systemFontScale(): Float = 1f

/** The app's version, as it is named by it. */
val appVersion: String get() = ResourceNames.VERSION

/** A size in bytes the way Android says one in its short form: 2.4 MB, 812 kB. */
fun fileSize(host: Host, bytes: Long): String {
    val units = listOf("B", "kB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (abs(value) >= 900 && unit < units.size - 1) {
        value /= 1000
        unit += 1
    }
    val format = NumberFormat.getNumberInstance(Device.language()).apply {
        maximumFractionDigits = if (unit == 0 || value >= 10) 0 else if (value >= 1) 1 else 2
    }
    return format.format(value) + " " + units[unit]
}

/** A number of hours in words: 12 hours. */
fun hoursInWords(hours: Long): String =
    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE).format(Measure(hours, MeasureUnit.HOUR))

/** The pattern the language writes these parts of a date in, in its own order. */
fun bestDatePattern(locale: Locale, skeleton: String): String = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)

/** A day the way the language writes one. */
fun mediumDate(host: Host, moment: Date): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(moment)

/** An hour the way the language writes one. */
fun timeOfDay(host: Host, moment: Date): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(moment)

/** Whether the language counts the hours to 24. */
fun is24Hour(host: Host): Boolean =
    (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()?.let { 'H' in it || 'k' in it } ?: true

/** This computer as a blog's list of devices will name it. */
fun deviceName(host: Host): String = Connect.deviceName

/** The text on the clipboard; null where there is none. */
fun pasteText(host: Host): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
}.getOrNull()

/** Text put on the clipboard; the label is a phone's to show. */
fun copyText(host: Host, label: String, text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

/** Where what may be cleared is kept. */
val cacheDir: File get() = File(Device.files, "cache").apply { mkdirs() }

/** A picture's bytes made into one to draw; null where they are no picture. */
fun decodeImage(data: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(data).toComposeImageBitmap() }.getOrNull()

/** A picture, a video or a file, as the system's dialog handed it over. */
typealias Picked = File

/** The system's own dialog for opening files: any number of them, or one. */
private fun pick(title: String, many: Boolean, endings: List<String>): List<File> {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isMultipleMode = many
    // Heard by a Mac's and Linux's dialog; Windows' shows every file, and what is not one is turned down when it is read.
    dialog.setFilenameFilter { _, name -> endings.isEmpty() || name.substringAfterLast('.', "").lowercase() in endings }
    dialog.isVisible = true
    return dialog.files.orEmpty().toList()
}

private val pictureEndings = listOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "mp4", "mov", "m4v")

/** A desktop has no photo library apart from its folders: the same dialog as for files. */
@Composable
fun rememberMediaPicker(onPicked: (List<Picked>) -> Unit): () -> Unit = rememberFilesPicker(onPicked)

@Composable
fun rememberFilesPicker(onPicked: (List<Picked>) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    return { pick("", many = true, pictureEndings).takeIf { it.isNotEmpty() }?.let { picked(it) } }
}

/** One text file, for the New post form. */
@Composable
fun rememberTextFilePicker(onPicked: (Picked) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    return { pick("", many = false, listOf("txt", "md", "markdown", "text")).firstOrNull()?.let { picked(it) } }
}

/** A desktop has no camera to ask for: the code is pasted as its line of text. */
@Composable
fun rememberCameraAsking(onAnswer: (Boolean) -> Unit): () -> Unit = { onAnswer(false) }

/**
 * What was picked, read into the form's card: a picture shrunk and
 * written as JPEG, as a phone writes it; a video whole, under the ending
 * it came with -- a desktop converts none, as a phone that could not.
 */
suspend fun shotOf(host: Host, item: Picked, index: Int, taken: List<String>): Shot = withContext(Dispatchers.IO) {
    val bytes = try {
        item.readBytes()
    } catch (e: Exception) {
        Device.log("picture not read", e)
        throw app.blogsh.android.model.Media.NotRead(app.blogsh.android.model.Media.unread(e, NetworkWatch.hasNetwork))
    }
    if (Kept.isVideo(item.name)) {
        val ending = item.extension.lowercase()
        val ext = if (ending in listOf("mp4", "mov", "m4v")) ending else "mov"
        val name = Pictures.freeName(Pictures.safeName(item.name, index, stem = "video", ext = ext), taken)
        return@withContext Shot(name, bytes, 0, 0, kind = Shot.Kind.Video, converted = false)
    }
    val shrunk = Machine.shrink(bytes, Pictures.MAX_EDGE, Pictures.QUALITY)
        ?: throw app.blogsh.android.model.Media.NotRead(app.blogsh.android.model.Media.Unread.Unreadable)
    val image = Image.makeFromEncoded(shrunk)
    val name = Pictures.freeName(Pictures.safeName(item.name, index), taken)
    Shot(name, shrunk, image.width, image.height, thumb = Machine.shrink(shrunk, 480, 75))
}

/** A text file picked, read and taken apart. */
suspend fun readTextFile(host: Host, item: Picked): TextFile.Read = withContext(Dispatchers.IO) {
    val data = try {
        item.inputStream().use { TextFile.bytes(it) }
    } catch (e: Exception) {
        Device.log("text file not read", e)
        throw TextFile.NotTaken(TextFile.Failure.Unreadable)
    }
    TextFile.read(data)
}
