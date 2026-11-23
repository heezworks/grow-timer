package app.sn62kr.grow.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import kotlinx.coroutines.launch

/** 첫 실행의 예시 12개(design.md 1절 Q9). 키는 아이콘 키로도 쓴다. 음악 감상은 넣지 않는다. */
enum class Preset(val key: String, @StringRes val label: Int) {
    READING("reading", R.string.preset_reading), INSTRUMENT("instrument", R.string.preset_instrument),
    DRAWING("drawing", R.string.preset_drawing), KNITTING("knitting", R.string.preset_knitting),
    WRITING("writing", R.string.preset_writing), LANGUAGE("language", R.string.preset_language),
    STUDY("study", R.string.preset_study), EXERCISE("exercise", R.string.preset_exercise),
    COOKING("cooking", R.string.preset_cooking), PHOTO("photo", R.string.preset_photo),
    GARDENING("gardening", R.string.preset_gardening), CODING("coding", R.string.preset_coding),
}

class OnboardingViewModel(private val g: Graph) : ViewModel() {
    /** (이름, 아이콘 키) 목록을 고른 순서대로 만든다. 색은 순서대로 돌려 쓴다. */
    fun finish(chosen: List<Pair<String, String>>, onDone: () -> Unit) = viewModelScope.launch {
        g.records.addActivities(chosen.mapIndexed { i, (name, icon) -> Triple(name, i, icon) })
        g.settings.setOnboarded()
        onDone()
    }
}

/** 첫 실행 한 화면(spec 2-0). 건너뛰면 "나의 시간" 하나가 생긴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val picked = rememberSaveable(saver = listSaver()) { mutableStateListOf() }
    val custom = rememberSaveable(saver = listSaver()) { mutableStateListOf() }
    var typing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.onboard_intro), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboard_question), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Preset.entries.forEach { p ->
                val on = p.key in picked
                FilterChip(on, { if (on) picked.remove(p.key) else picked.add(p.key) }, { Text(stringResource(p.label)) })
            }
            custom.forEach { name -> FilterChip(true, { custom.remove(name) }, { Text(name) }) }
            FilterChip(false, { typing = true }, { Text(stringResource(R.string.onboard_custom)) })
        }
        if (typing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(30) }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.onboard_custom_hint)) })
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    val n = draft.trim()
                    if (n !in custom) custom.add(n)
                    draft = ""; typing = false
                }) { Text(stringResource(R.string.save)) }
            }
        }
        Text(stringResource(R.string.onboard_later), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = picked.isNotEmpty() || custom.isNotEmpty(),
            onClick = {
                val list = Preset.entries.filter { it.key in picked }.map { ctx.getString(it.label) to it.key } + custom.map { it to "custom" }
                vm.finish(list, onDone)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.onboard_start)) }
        TextButton(onClick = { vm.finish(listOf(ctx.getString(R.string.default_activity) to "default"), onDone) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.onboard_skip))
        }
    }
}

private fun listSaver() = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
    save = { it.toList() }, restore = { mutableStateListOf<String>().apply { addAll(it) } },
)
