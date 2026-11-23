package app.sn62kr.grow.ui.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import app.sn62kr.grow.core.garden.Garden
import app.sn62kr.grow.core.stats.YearGrid
import app.sn62kr.grow.net.FriendList
import app.sn62kr.grow.net.FriendView
import app.sn62kr.grow.net.Friends
import app.sn62kr.grow.ui.SceneView
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.YearGridView
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.review.Stars
import app.sn62kr.grow.ui.review.lineText
import app.sn62kr.grow.net.OpenLibrary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface FriendsState {
    data object Loading : FriendsState
    data object Unavailable : FriendsState
    data object Off : FriendsState
    data class On(val list: FriendList) : FriendsState
    data class Problem(val res: Int, val code: String?) : FriendsState
}

class FriendsViewModel(private val g: Graph) : ViewModel() {
    val state = MutableStateFlow<FriendsState>(FriendsState.Loading)
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val today = g.today.stateIn(viewModelScope, SharingStarted.Eagerly, g.clock.today())
    val busy = MutableStateFlow(false)

    init { refresh() }

    private fun <T> problem(o: Friends.Out<T>): Int = when (o) {
        Friends.Out.Offline -> R.string.fr_offline
        Friends.Out.Paused -> R.string.fr_paused
        Friends.Out.NotFound -> R.string.fr_not_found
        Friends.Out.Self -> R.string.fr_self
        Friends.Out.Full -> R.string.fr_full
        else -> R.string.fr_failed
    }

    fun refresh() = viewModelScope.launch {
        if (!g.friends.available) { state.value = FriendsState.Unavailable; return@launch }
        val code = g.settings.friendCode.first()
        if (code == null) { state.value = FriendsState.Off; return@launch }
        g.friends.uploadIfDue()
        state.value = when (val o = g.friends.list()) {
            is Friends.Out.Ok -> FriendsState.On(o.v)
            else -> FriendsState.Problem(problem(o), code)
        }
    }

    fun turnOn() = viewModelScope.launch {
        busy.value = true
        when (val o = g.friends.enable()) { is Friends.Out.Ok -> refresh(); else -> messages.emit(problem(o)) }
        busy.value = false
    }

    fun turnOff() = viewModelScope.launch { g.friends.disable(); refresh() }

    fun add(code: String) = viewModelScope.launch {
        when (val o = g.friends.add(code)) {
            is Friends.Out.Ok -> { messages.emit(if (o.v) R.string.fr_linked else R.string.fr_sent); refresh() }
            else -> messages.emit(problem(o))
        }
    }

    fun remove(code: String) = viewModelScope.launch { g.friends.remove(code); refresh() }
    fun block(code: String) = viewModelScope.launch { g.friends.block(code); refresh() }
    fun report(code: String, reason: String) = viewModelScope.launch {
        if (g.friends.report(code, reason) is Friends.Out.Ok) messages.emit(R.string.fr_reported) else messages.emit(R.string.fr_failed)
    }

    fun weekStart() = g.weekStart()
}

/** 친구 탭(D59): 코드 주고받기, 친구의 테라스와 격자 보기, 끊기·막기·신고. 서버가 쉬면 이 화면만 쉰다. */
@Composable
fun FriendsScreen(vm: FriendsViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(ctx.getString(it)) } }

    Scaffold(modifier, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val s = st) {
                FriendsState.Loading -> Unit
                FriendsState.Unavailable -> Message(R.string.fr_soon)
                FriendsState.Off -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.fr_intro), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.fr_what), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = vm::turnOn, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fr_turn_on)) }
                }
                is FriendsState.Problem -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.code?.let { MyCode(it) }
                    Text(stringResource(s.res), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { vm.refresh() }) { Text("↻") }
                }
                is FriendsState.On -> FriendsList(vm, s.list)
            }
        }
    }
}

@Composable
private fun Message(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))

@Composable
private fun MyCode(code: String) {
    val ctx = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.fr_my_code), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(code.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                    modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", code))
                    android.widget.Toast.makeText(ctx, R.string.fr_copied, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.fr_copy)) }
            }
            Text(stringResource(R.string.fr_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FriendsList(vm: FriendsViewModel, l: FriendList) {
    var input by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MyCode(l.me) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { v -> input = v.uppercase().filter { it.isLetterOrDigit() }.take(8) }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.fr_add_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (input.length == 8) { vm.add(input); input = "" } }))
                Button(onClick = { vm.add(input); input = "" }, enabled = input.length == 8) { Text(stringResource(R.string.fr_add)) }
            }
        }
        if (l.waiting.isNotEmpty()) item {
            Text(stringResource(R.string.fr_waiting, l.waiting.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (l.friends.isEmpty()) item { Text(stringResource(R.string.fr_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(l.friends, key = { it.code }) { f -> FriendCard(vm, f) }
        item { TextButton(onClick = vm::turnOff) { Text(stringResource(R.string.fr_turn_off), color = MaterialTheme.colorScheme.error) } }
    }
}

/** 친구 한 명: 그 사람의 숫자로 테라스와 올해 격자를 이 폰에서 다시 그린다(그림을 주고받지 않는다). */
@Composable
private fun FriendCard(vm: FriendsViewModel, f: FriendView) {
    val ctx = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var reportMenu by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            SceneView(Garden.of(f.totalSec, f.gardenSeed), f.gardenSeed, f.pose, stringResource(R.string.fr_desc, f.code), Modifier.fillMaxWidth(), cropTop = 34f)
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.code.chunked(4).joinToString(" "), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.fr_total, Text.duration(ctx, f.totalSec)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.fr_unfriend)) }, { menu = false; vm.remove(f.code) })
                        DropdownMenuItem({ Text(stringResource(R.string.fr_block)) }, { menu = false; vm.block(f.code) })
                        DropdownMenuItem({ Text(stringResource(R.string.fr_report)) }, { menu = false; reportMenu = true })
                    }
                    DropdownMenu(reportMenu, { reportMenu = false }) {
                        listOf("spam" to R.string.fr_report_spam, "inappropriate" to R.string.fr_report_bad, "other" to R.string.fr_report_other).forEach { (k, r) ->
                            DropdownMenuItem({ Text(stringResource(r)) }, { reportMenu = false; vm.report(f.code, k) })
                        }
                    }
                }
            }
            YearGridView(YearGrid.build(today.year, vm.weekStart(), f.daily, today), onDay = {}, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp, top = 4.dp), cell = 10.dp)
            // 고르기식 후기: 번호로 이 폰의 언어로 다시 쓴다. 책은 ID로 Open Library에서 제목을 받는다
            f.reviews.take(5).forEach { r ->
                val color = f.acts.firstOrNull { it.first == r.icon }?.second ?: 0
                var title by remember(r.catalog) { mutableStateOf<String?>(null) }
                LaunchedEffect(r.catalog) { r.catalog?.let { title = OpenLibrary.title(it) } }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(color)
                    Column(Modifier.weight(1f)) {
                        title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(lineText(r.line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Stars(r.stars)
                }
            }
            if (f.reviews.isNotEmpty()) androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}
