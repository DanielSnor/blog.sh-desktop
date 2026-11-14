package app.blogsh.android.ui.screens

import androidx.compose.runtime.Composable
import app.blogsh.android.ui.Host

/** A desktop has no camera the app reads a code with: the code is pasted as its line of text. */
object CodeScanner {
    fun isOffered(host: Host): Boolean = false

    fun isAllowed(host: Host): Boolean = false
}

@Composable
fun CodeScannerScreen(onRead: (String) -> Unit, onDismiss: () -> Unit) {
}
