package app.blogsh.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import app.blogsh.android.model.KeyStore
import app.blogsh.android.model.ListAnswer
import app.blogsh.android.model.Pairing
import app.blogsh.android.model.PairingCode
import app.blogsh.android.model.PairingError
import app.blogsh.android.model.QueueAnswer
import app.blogsh.android.model.TrustOnFirstUse
import app.blogsh.android.model.VersionAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * A blog connected by the code `./blog.sh pair` shows, given as its line
 * of text: a key of the app's own is made, handed in with the code's,
 * and the blog is the app's from then on. A code that did not take
 * leaves nothing behind -- no blog, no key.
 */
object Connect {
    /** This computer as a blog's list of devices will name it. */
    val deviceName: String
        get() = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.substringBefore('.')
            ?: "desktop"

    /** The code read, or why it is not one -- in words. */
    fun read(text: String): Result<PairingCode> = runCatching { PairingCode(text.trim()) }

    /** Throws a `PairingError`; `words` says it. */
    suspend fun byCode(code: PairingCode) {
        val blog = Blog(host = code.host, port = code.port, user = code.user, name = code.site ?: "")
        var made: String? = null
        try {
            val handed = withContext(Dispatchers.IO) {
                try {
                    KeyStore.makeKey(blog.keyAccount)
                } catch (e: Exception) {
                    throw PairingError.NoKey
                }
                made = blog.keyAccount
                val line = KeyStore.publicKeyLine(blog.keyAccount).split(" ").take(2).joinToString(" ")
                Pairing.handIn(line, deviceName, code)
            }
            // The server that answered is the one this blog's connections expect from now on.
            BlogShelf.notes.write(TrustOnFirstUse.notesKey(code.host, code.port), handed.server)
            Blogs.adopt(blog.copy(pairedAs = Pairing.known(handed.device, deviceName)))
        } catch (e: Throwable) {
            made?.let { name -> withContext(Dispatchers.IO) { runCatching { KeyStore.deleteKey(name) } } }
            throw e
        }
    }

    fun words(why: Throwable, code: PairingCode): String = when (why) {
        PairingError.Spent -> Words.say("the_code_is_no_longer_good_it")
        PairingError.WrongServer -> Words.say("another_machine_answered_than_the_one_the")
        // Said as what to check, not as what the network library reported.
        is PairingError.Unreachable -> Words.say("the_server_did_not_answer_is_this", code.host)
        is PairingError.Refused -> Words.say("the_blog_said_no", why.words)
        else -> Words.say("the_app_could_not_make_its_key")
    }
}

/**
 * What the first screen of the phone says of the open blog, read the way
 * the phone reads it -- over one connection: who the blog is, its queue
 * and its drafts.
 */
class GlanceState {
    var identity by mutableStateOf<VersionAnswer?>(null)
        private set
    var queue by mutableStateOf<QueueAnswer?>(null)
        private set
    var drafts by mutableStateOf<ListAnswer?>(null)
        private set
    var problem by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    suspend fun load() {
        loading = true
        try {
            val answers = Engine.batch(listOf(listOf("version"), listOf("queue"), listOf("list", "--drafts")))
            val answer = Engine.decode<VersionAnswer>(answers[0])
            identity = answer
            queue = Engine.decode<QueueAnswer>(answers[1])
            drafts = Engine.decode<ListAnswer>(answers[2])
            problem = null
            Blogs.update { it.copy(name = answer.site.name, claim = answer.site.claim, url = answer.site.url, maxMb = answer.maxMb) }
        } catch (e: Throwable) {
            problem = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }
}
