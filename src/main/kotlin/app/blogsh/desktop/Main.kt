package app.blogsh.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.blogsh.android.model.Engine
import app.blogsh.android.ui.BackKeys
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost
import java.awt.Dimension

fun main() {
    Machine.install()
    Engine.installCrypto()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "./blog.sh",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
            // Esc is the way back: out of the sheet in front, then the screen beside the menu.
            onPreviewKeyEvent = { it.type == KeyEventType.KeyDown && it.key == Key.Escape && BackKeys.back() },
        ) {
            // Narrower than this, the menu and the screen beside it have no room to stand.
            window.minimumSize = Dimension(900, 600)
            App()
        }
    }
}

/**
 * The Android app's screens as a tablet held on its side shows them: the
 * menu, and beside it the screen that is open.
 */
@Composable
fun App() {
    BlogshTheme {
        val nav = remember { Nav() }
        val home = remember { HomeState() }
        CompositionLocalProvider(LocalNav provides nav) {
            NavHost(nav) { HomeScreen(home) }
        }
    }
}
