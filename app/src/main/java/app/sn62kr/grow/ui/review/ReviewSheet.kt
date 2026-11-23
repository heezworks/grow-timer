package app.sn62kr.grow.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.data.PresetLines
import app.sn62kr.grow.data.Review
import app.sn62kr.grow.net.OpenLibrary
import kotlinx.coroutines.launch

/**
 * 정해진 한 줄의 문구(번호 → 언어별 글). 번호 순서를 바꾸지 않는다(친구 폰이 번호로 읽는다).
 * 이름으로 찾지(getIdentifier) 않고 R을 직접 쓴다 — 릴리스의 리소스 줄이기가 이름으로만 쓰는 글을 지웠다(M6에서 찾음).
 */
internal val LINES = intArrayOf(
    R.string.review_line_0, R.string.review_line_1, R.string.review_line_2, R.string.review_line_3,
    R.string.review_line_4, R.string.review_line_5, R.string.review_line_6, R.string.review_line_7,
    R.string.review_line_8, R.string.review_line_9, R.string.review_line_10, R.string.review_line_11,
)

@Composable
fun lineText(i: Int): String = LINES.getOrNull(i)?.let { stringResource(it) } ?: ""

/** ★★★☆☆ (읽어 주는 글은 "별 3개"). */
@Composable
fun Stars(n: Int, modifier: Modifier = Modifier) {
    val d = stringResource(R.string.rv_stars, n)
    Text("★".repeat(n) + "☆".repeat(5 - n), color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, modifier = modifier.semantics { contentDescription = d })
}

/** 활동 하나의 후기들(고르기식, D59). 자유 글 칸이 없다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    activity: Activity, reviews: List<Review>, friendsOn: Boolean,
    onDismiss: () -> Unit, onAdd: (stars: Int, line: Int, catalogId: String?, title: String?, shared: Boolean) -> Unit, onDelete: (Review) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Review?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.rv_title, activity.name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (reviews.isEmpty()) Text(stringResource(R.string.rv_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            reviews.forEach { r ->
                ListItem(
                    headlineContent = { Text(r.title ?: lineText(r.line)) },
                    supportingContent = { Column { if (r.title != null) Text(lineText(r.line)); Stars(r.stars) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { deleting = r },
                )
            }
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.rv_add)) }
        }
    }
    if (adding) AddReviewDialog(activity, friendsOn, onDismiss = { adding = false }) { s, l, id, t, sh -> adding = false; onAdd(s, l, id, t, sh) }
    deleting?.let { r ->
        AlertDialog(onDismissRequest = { deleting = null }, text = { Text(r.title ?: lineText(r.line)) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(r) }) { Text(stringResource(R.string.rv_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReviewDialog(
    activity: Activity, friendsOn: Boolean, onDismiss: () -> Unit,
    onSave: (stars: Int, line: Int, catalogId: String?, title: String?, shared: Boolean) -> Unit,
) {
    var stars by rememberSaveable { mutableStateOf(4) }
    var line by rememberSaveable { mutableStateOf<Int?>(null) }
    var book by remember { mutableStateOf<OpenLibrary.Book?>(null) }
    var searching by remember { mutableStateOf(false) }
    var shared by rememberSaveable { mutableStateOf(true) }
    val isBook = activity.iconKey == "reading"
    // 책 줄(8~10)은 독서일 때만, 손 줄(11)은 독서가 아닐 때만
    val lines = (0 until PresetLines.COUNT).filter { if (isBook) it != 11 else it !in 8..10 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rv_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row {
                    (1..5).forEach { i ->
                        val d = stringResource(R.string.rv_stars, i)
                        Text(if (i <= stars) "★" else "☆", fontSize = 32.sp, color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.selectable(i == stars, role = Role.RadioButton) { stars = i }.padding(4.dp).semantics { contentDescription = d })
                    }
                }
                Text(stringResource(R.string.rv_pick_line), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { i -> FilterChip(line == i, { line = i }, { Text(lineText(i)) }) }
                }
                if (isBook) {
                    book?.let { Text(it.title + (it.author?.let { a -> " · $a" } ?: ""), style = MaterialTheme.typography.bodyLarge) }
                    TextButton(onClick = { searching = true }) { Text(stringResource(if (book == null) R.string.rv_book else R.string.rv_book_change)) }
                }
                if (friendsOn) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().selectable(shared, role = Role.Switch) { shared = !shared }) {
                    Text(stringResource(R.string.rv_share), modifier = Modifier.weight(1f))
                    Switch(checked = shared, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = line != null, onClick = { onSave(stars, line!!, book?.id, book?.title, friendsOn && shared) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (searching) BookSearchDialog(onDismiss = { searching = false }) { book = it; searching = false }
}

/** Open Library 책 찾기. 찾는 말만 Open Library로 간다(우리 서버는 모른다). */
@Composable
private fun BookSearchDialog(onDismiss: () -> Unit, onPick: (OpenLibrary.Book) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OpenLibrary.Book>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() { if (q.isBlank()) return; scope.launch { val r = OpenLibrary.search(q); failed = r == null; results = r } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rv_book)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.rv_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go() }))
                Text(stringResource(R.string.rv_search_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    when {
                        failed -> Text(stringResource(R.string.rv_search_offline))
                        results?.isEmpty() == true -> Text(stringResource(R.string.rv_search_none))
                        else -> results?.forEach { b ->
                            ListItem(
                                headlineContent = { Text(b.title) },
                                supportingContent = { Text(listOfNotNull(b.author, b.year?.toString()).joinToString(" · ")) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.clickable { onPick(b) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { go() }) { Text(stringResource(R.string.rv_search)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
