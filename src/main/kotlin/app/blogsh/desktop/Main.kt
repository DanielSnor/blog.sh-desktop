package app.blogsh.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import kotlinx.coroutines.launch

fun main() {
    Machine.install()
    Engine.installCrypto()
    application {
        Window(onCloseRequest = ::exitApplication, title = "./blog.sh", state = rememberWindowState(width = 960.dp, height = 720.dp)) {
            App(dark = isSystemInDarkTheme())
        }
    }
}

@Composable
fun App(dark: Boolean) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (Blogs.current == null) AddBlog() else Glance(remember(Blogs.currentId) { GlanceState() })
        }
    }
}

@Composable
private fun AddBlog() {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Words.say("add_a_blog"), style = MaterialTheme.typography.headlineSmall)
        Text(Words.say("with_a_code"), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text, onValueChange = { text = it; problem = null },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !connecting,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text("blogsh://pair?…") },
        )
        Button(
            enabled = !connecting && text.isNotBlank(),
            onClick = {
                val code = Connect.read(text).getOrNull()
                if (code == null) {
                    problem = Words.say("the_code_is_not_whole_copy_the")
                } else {
                    scope.launch {
                        connecting = true
                        problem = null
                        try {
                            Connect.byCode(code)
                        } catch (e: Throwable) {
                            problem = Connect.words(e, code)
                        } finally {
                            connecting = false
                        }
                    }
                }
            },
        ) { Text(Words.say(if (connecting) "connecting" else "connect")) }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun Glance(state: GlanceState, load: Boolean = true) {
    val scope = rememberCoroutineScope()
    if (load) LaunchedEffect(state) { state.load() }

    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Blogs.current?.label ?: "", style = MaterialTheme.typography.headlineSmall)
            TextButton(enabled = !state.loading, onClick = { scope.launch { state.load() } }) { Text("↻") }
        }
        state.identity?.let { said ->
            if (said.site.claim.isNotEmpty()) Text(said.site.claim)
            Text("${said.site.url} · blog.sh ${said.engine}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))

        Text(Words.say("scheduled"), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        val rows = state.queue?.queue.orEmpty()
        if (state.queue != null && rows.isEmpty()) Text(Words.say("the_queue_is_empty"))
        rows.forEach { row -> Text("${row.position}. ${row.title}  ·  ${row.date}") }
        Spacer(Modifier.height(12.dp))

        Text(Words.say("drafts_in_progress"), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        val posts = state.drafts?.posts.orEmpty()
        if (state.drafts != null && posts.isEmpty()) Text(Words.say("no_drafts_in_progress"))
        posts.forEach { post -> Text(post.title ?: post.slug) }
    }
}
