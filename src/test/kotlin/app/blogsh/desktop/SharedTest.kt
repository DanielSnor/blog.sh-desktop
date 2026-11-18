package app.blogsh.desktop

import app.blogsh.android.model.Preview
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What this app has taken over from the Android app (shared/) does what
 * it was taken over for. The model's own tests live with it in the
 * Android app; these watch the copy here.
 */
class SharedTest {
    /** A numbered list begins where its first item does, as the blog draws it (./blog.sh 1.11.1). */
    @Test
    fun aNumberedListKeepsItsFirstNumberInThePreview() {
        val from5 = Preview.render("5. five\n6. six\n")
        assertTrue(from5, from5.contains("<ol start=\"5\">"))
        val from1 = Preview.render("1. one\n2. two\n")
        assertTrue(from1, from1.contains("<ol>"))
    }
}
