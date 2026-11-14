package app.blogsh.android.ui

import androidx.compose.runtime.Composable
import app.blogsh.android.R
import app.blogsh.android.model.Herald
import app.blogsh.android.model.PostLink

/**
 * A post's address handed to somebody: a desktop has no share sheet of
 * the system's, so the address goes to the clipboard, and the app says so.
 */
fun share(host: Host, link: PostLink) {
    copyText(host, link.title, link.url)
    Herald.shared.say(host.getString(R.string.copied))
}

/**
 * The key that hands a post's address to somebody. It stands in the bar
 * of every screen that is about one post, once the engine has said where
 * the post is.
 */
@Composable
fun ShareKey(link: PostLink) {
    val host = rememberHost()
    BarKey(Symbols.squareAndArrowUp, stringResource(R.string.share_the_link)) { share(host, link) }
}
