package app.sn62kr.grow.ui.activities

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.theme.ActivityColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivitiesViewModel(private val g: Graph) : ViewModel() {
    val all: StateFlow<List<Activity>> = g.records.allActivities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, color: Int) = viewModelScope.launch { g.records.addActivity(name, color, "custom") }
    fun save(a: Activity) = viewModelScope.launch { g.records.updateActivity(a) }
    fun setHidden(a: Activity, hidden: Boolean) = viewModelScope.launch { g.records.setArchived(a, hidden) }
    fun deleteWithRecords(a: Activity) = viewModelScope.launch { g.records.deleteActivityWithRecords(a.id) }
    suspend fun recordedSec(a: Activity) = g.records.recordedSec(a.id)

    /** 보이는 활동 안에서 한 칸 옮긴다. */
    fun move(a: Activity, by: Int) = viewModelScope.launch {
        val shown = all.value.filter { !it.archived }.toMutableList()
        val i = shown.indexOfFirst { it.id == a.id }
        val j = i + by
        if (i < 0 || j !in shown.indices) return@launch
        shown.add(j, shown.removeAt(i))
        g.records.reorder(shown)
    }
}

/** 활동 관리(spec 2-7). 지우기의 기본은 보관(숨기기)이다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(vm: ActivitiesViewModel, onBack: () -> Unit) {
    val all by vm.all.collectAsStateWithLifecycle()
    val shown = all.filter { !it.archived }
    val hidden = all.filter { it.archived }
    var editing by remember { mutableStateOf<Activity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.act_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.act_add)) })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    leadingContent = { ColorDot(a.colorIndex, 16.dp) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.move(a, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.act_up)) }
                            IconButton(onClick = { vm.move(a, 1) }, enabled = i < shown.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.act_down)) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { editing = a },
                )
            }
            if (hidden.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.act_hidden), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
                }
                itemsIndexed(hidden, key = { _, a -> a.id }) { _, a ->
                    ListItem(
                        headlineContent = { Text(a.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { ColorDot(a.colorIndex, 16.dp) },
                        trailingContent = { TextButton(onClick = { vm.setHidden(a, false) }) { Text(stringResource(R.string.act_show)) } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { editing = a },
                    )
                }
            }
        }
    }

    if (creating) EditDialog(null, vm, onDone = { creating = false })
    editing?.let { a -> EditDialog(a, vm, onDone = { editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditDialog(a: Activity?, vm: ActivitiesViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(a?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(a?.colorIndex ?: vm.all.value.size % ActivityColors.all.size) }
    var askDelete by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (a == null) stringResource(R.string.act_new) else a.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.act_name)) }, singleLine = true)
                Text(stringResource(R.string.act_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityColors.all.forEachIndexed { i, c ->
                        val on = i == color
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .padding(if (on) 5.dp else 0.dp)
                                .semantics { role = Role.RadioButton; selected = on; contentDescription = "${i + 1}" }
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { ColorDot(i, if (on) 26.dp else 36.dp) }
                    }
                }
                if (a != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!a.archived) TextButton(onClick = { vm.setHidden(a, true); onDone() }) { Text(stringResource(R.string.act_hide)) }
                        TextButton(onClick = { scope.launch { askDelete = vm.recordedSec(a) } }) {
                            Text(stringResource(R.string.act_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                if (a == null) vm.add(name, color) else vm.save(a.copy(name = name.trim(), colorIndex = color))
                onDone()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )

    val recorded = askDelete
    if (a != null && recorded != null) {
        if (recorded == 0L) { vm.deleteWithRecords(a); onDone() } else AlertDialog(
            onDismissRequest = { askDelete = null },
            text = { Text(stringResource(R.string.act_delete_ask, Text.duration(ctx, recorded))) },
            // 기본은 남기기: 숨기기와 같다(design.md 2절)
            confirmButton = { TextButton(onClick = { vm.setHidden(a, true); onDone() }) { Text(stringResource(R.string.act_delete_keep)) } },
            dismissButton = {
                TextButton(onClick = { vm.deleteWithRecords(a); onDone() }) {
                    Text(stringResource(R.string.act_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
