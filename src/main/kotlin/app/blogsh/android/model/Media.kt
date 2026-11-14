package app.blogsh.android.model

import app.blogsh.android.ui.Host

/**
 * What the screens know of a picked item that could not be read -- the
 * Android app's Media, of which a desktop needs only this: the reading
 * itself is OnDesktop.kt's (`shotOf`).
 */
object Media {
    /** Why nothing could be read of a picked item. */
    enum class Unread {
        /** What is missing could not be fetched: there is no network to fetch it over. */
        NeedsNetwork,
        Unreadable,
    }

    /** Nothing could be read of a picked item, and the likely reason. */
    class NotRead(val why: Unread) : Exception(why.name)

    /** What a failed read was: a network that is not there, in so many words or by failing while there is none. */
    fun unread(error: Throwable?, online: Boolean): Unread {
        var next = error
        var steps = 0
        while (next != null && steps < 8) {
            if (next is java.net.UnknownHostException || next is java.net.SocketException || next is java.net.SocketTimeoutException) return Unread.NeedsNetwork
            next = next.cause
            steps += 1
        }
        return if (online) Unread.Unreadable else Unread.NeedsNetwork
    }
}

/** A word of the screens', said where the model's are: by its id among the app's resources. */
fun Spoken.say(id: Int, vararg args: Any): String = Host().getString(id, *args)
