package app.sn62kr.grow.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sn62kr.grow.R

/**
 * 앱에 들어가는 부품(시험 전용 부품은 빼고). 새 부품을 넣으면 여기와 spec-draft 6절 표에 같이 올린다(CLAUDE.md).
 * 라이선스 이름은 각 저장소 기준 [단계 4에서 LICENSE 파일로 다시 확인].
 */
private data class Lib(val name: String, val license: String, val url: String)

private val APACHE = "Apache License 2.0"
private val LIBS = listOf(
    Lib("Kotlin Standard Library", APACHE, "https://github.com/JetBrains/kotlin"),
    Lib("kotlinx.coroutines", APACHE, "https://github.com/Kotlin/kotlinx.coroutines"),
    Lib("kotlinx.serialization", APACHE, "https://github.com/Kotlin/kotlinx.serialization"),
    Lib("AndroidX Core · Activity · Lifecycle", APACHE, "https://developer.android.com/jetpack/androidx"),
    Lib("Jetpack Compose (UI · Foundation · Material 3 · Icons)", APACHE, "https://developer.android.com/jetpack/compose"),
    Lib("Navigation Compose", APACHE, "https://developer.android.com/jetpack/androidx/releases/navigation"),
    Lib("Room", APACHE, "https://developer.android.com/jetpack/androidx/releases/room"),
    Lib("DataStore", APACHE, "https://developer.android.com/jetpack/androidx/releases/datastore"),
    Lib("Glance", APACHE, "https://developer.android.com/jetpack/androidx/releases/glance"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.licenses_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.licenses_app), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            items(LIBS) { l ->
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = { Text(l.license) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) } },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(APACHE) },
                    supportingContent = { Text("https://www.apache.org/licenses/LICENSE-2.0") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))) } },
                )
            }
        }
    }
}
