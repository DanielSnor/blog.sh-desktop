package app.blogsh.desktop

import app.blogsh.android.model.Word
import java.util.Locale

/**
 * The app's words: the Android app's files of them, read as they are
 * written (`values/strings.xml`, `values-cs/…`) and carried in this app
 * under `words/`. The two apps say the same things the same way, and a
 * word is translated once. A word missing in a language is said in
 * English; one missing in English too is said by its name, so that a
 * gap shows instead of an empty line.
 */
object Words {
    private val files = listOf("strings.xml", "android.xml", "android_d.xml")

    @Volatile
    private var held: Pair<Locale, Map<String, String>>? = null

    private fun texts(locale: Locale): Map<String, String> {
        held?.let { (heldFor, texts) -> if (heldFor == locale) return texts }
        val english = read("values")
        val texts = if (locale.language == "en") english else english + read("values-${locale.language}")
        held = locale to texts
        return texts
    }

    private fun read(directory: String): Map<String, String> = files.flatMap { name ->
        val xml = Words::class.java.getResourceAsStream("/words/$directory/$name")?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@flatMap emptyList()
        parse(xml).toList()
    }.toMap()

    /** The `<string>` entries of one file, unescaped as Android unescapes them. */
    fun parse(xml: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { m ->
            var text = m.groupValues[2]
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
            text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
                .replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@").replace("\\\\", "\\")
            m.groupValues[1] to text
        }

    /** The words under this name in the app's language, with what they say filled in. */
    fun say(name: String, vararg args: Any): String {
        val locale = app.blogsh.android.model.Device.language()
        val text = texts(locale)[name] ?: return name
        return if (args.isEmpty()) text.replace("%%", "%") else String.format(locale, text, *args)
    }

    fun say(word: Word, args: Array<out Any>): String = say(word.name, *args)
}
