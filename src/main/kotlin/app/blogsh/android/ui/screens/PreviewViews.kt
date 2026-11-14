package app.blogsh.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.Hairline
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.SheetDialog
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.Typed
import app.blogsh.android.ui.cacheDir
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.rememberHost
import app.blogsh.android.ui.stringResource
import app.blogsh.android.ui.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.Desktop
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The text as the blog will show it, near enough. A phone draws the page
 * in the sheet itself; a desktop has no web view of its own, so the page
 * is written beside the app's files and opened in the browser -- at once,
 * and again from the sheet.
 *
 * `lang` is the language the text is in, when it is not the reader's own:
 * a translation's.
 */
@Composable
fun PreviewSheet(title: String, markdown: String, shown: Map<String, Preview.Shown>, lang: String? = null, onDismiss: () -> Unit) {
    val host = rememberHost()
    val language = lang ?: Locale.getDefault().language.ifEmpty { "en" }
    val page = remember(title, markdown, shown, language) {
        val spoken = Preview.Words(
            missing = { host.getString(R.string.picture_no_preview_on_this_device, it) },
            glued = { host.getString(R.string.a_picture_has_to_stand_on_a, it) },
        )
        Preview.document(title, Preview.render(markdown, shown, spoken), language)
    }
    val file = remember(page) { File(cacheDir, "preview.html") }
    fun open() {
        runCatching {
            file.writeText(page)
            Desktop.getDesktop().browse(file.toURI())
        }
    }
    LaunchedEffect(page) { withContext(Dispatchers.IO) { open() } }
    PaperSheet(onDismiss, name = stringResource(R.string.preview), actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) }) {
        Plate {
            row { Command(stringResource(R.string.show_the_preview_on_the_web), Symbols.safari) { open() } }
        }
        Hint(
            stringResource(R.string.near_enough_not_exact_the_blog_itself),
            Modifier.padding(horizontal = Theme.gutter).padding(top = 10.dp),
        )
    }
}

/**
 * Bytes as a picture no larger than it will be drawn: a photograph whole
 * is some twenty megabytes of memory, and a card shows it the size of a
 * thumb. Null when the bytes are not a picture.
 */
internal fun pictureOf(bytes: ByteArray, edge: Int): ImageBitmap? = runCatching {
    val image = SkiaImage.makeFromEncoded(bytes)
    val long = max(image.width, image.height)
    if (long <= edge) return@runCatching image.toComposeImageBitmap()
    val scale = edge.toDouble() / long
    val width = max(1, (image.width * scale).roundToInt())
    val height = max(1, (image.height * scale).roundToInt())
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()))
    surface.makeImageSnapshot().toComposeImageBitmap()
}.getOrNull()

/**
 * The shots of a form, one to a page and large: what each of them is, and
 * under it the line that describes it -- written while looking at it.
 * `current` is the shot it opens on; a description changed here comes
 * back through `onShot` as the shot with its new words.
 */
@Composable
fun ShotsViewer(shots: List<Shot>, current: String, onShot: (Shot) -> Unit, onDismiss: () -> Unit) {
    SheetDialog(onDismissRequest = onDismiss) {
        val pager = rememberPagerState(initialPage = shots.indexOfFirst { it.id == current }.coerceAtLeast(0)) { shots.size }
        Typed { Column(Modifier.fillMaxSize().background(Theme.paper)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Theme.gutter, vertical = 14.dp)) {
                if (pager.currentPage < shots.size) {
                    EngineLabel(stringResource(R.string.of, pager.currentPage + 1, shots.size), Modifier.alignByBaseline(), size = 13f)
                }
                Spacer(Modifier.weight(1f))
                Pressable(onDismiss, modifier = Modifier.alignByBaseline()) {
                    Text(stringResource(R.string.done), color = Theme.accent, style = ui(16f, FontWeight.SemiBold))
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { shots[it].id }) { page ->
                val shot = shots[page]
                ShotPage(shot) { onShot(shot.withAlt(it)) }
            }
        } }
    }
}

/**
 * One shot, large. A picture is drawn; a video is played by the system's
 * own player, which a desktop has and the app does not.
 */
@Composable
private fun ShotPage(shot: Shot, onAlt: (String) -> Unit) {
    var image by remember(shot.id) { mutableStateOf<ImageBitmap?>(null) }
    val place = remember(shot.id) { File(cacheDir, "look-" + shot.id + "-" + shot.name) }
    LaunchedEffect(shot.id) {
        image = withContext(Dispatchers.Default) { if (shot.kind == Shot.Kind.Video) shot.poster?.let { pictureOf(it, 1024) } else pictureOf(shot.data, 2048) }
    }
    DisposableEffect(shot.id) { onDispose { place.delete() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Theme.gutter), contentAlignment = Alignment.Center) {
            image?.let { Image(it, contentDescription = shot.alt.ifEmpty { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Plate(Modifier.padding(horizontal = Theme.gutter).padding(bottom = 16.dp)) {
            if (shot.kind == Shot.Kind.Video) {
                row {
                    Command(stringResource(R.string.android_d_play), Symbols.playFill) {
                        runCatching {
                            place.writeBytes(shot.data)
                            Desktop.getDesktop().open(place)
                        }
                    }
                }
            }
            row {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        shot.name, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    Text(Delivery.size(shot.data.size), color = Theme.muted, style = mono(11f, bold = false), modifier = Modifier.alignByBaseline())
                }
            }
            row {
                // One line to four: a description is a sentence, sometimes two.
                val style = ui(16f)
                BasicTextField(
                    value = shot.alt, onValueChange = onAlt, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    textStyle = style.copy(color = Theme.ink), cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (shot.alt.isEmpty()) Text(stringResource(R.string.no_description_yet), color = Theme.muted, style = style, maxLines = 1)
                            inner()
                        }
                    },
                )
            }
        }
    }
}
