package app.sn62kr.grow.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import android.widget.Toast
import app.sn62kr.grow.core.stats.YearGrid
import app.sn62kr.grow.share.ShareCard
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import app.sn62kr.grow.core.draw.Pose
import app.sn62kr.grow.core.draw.SceneArt
import app.sn62kr.grow.core.garden.Garden
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.ui.SceneCanvas
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.sceneInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 폴라로이드 한 장의 입력: 그 세션과 그 세션이 끝난 때까지 쌓인 시간. 그림은 저장하지 않는다(D43). */
data class Polaroid(val session: Session, val activity: Activity?, val totalAtEndSec: Long)

/** 5분 넘는 세션마다 한 장(design.md 2절). */
const val POLAROID_MIN_SEC = 5 * 60

fun polaroids(chrono: List<Session>, activities: List<Activity>): List<Polaroid> {
    val byId = activities.associateBy { it.id }
    var total = 0L
    val out = ArrayList<Polaroid>()
    for (s in chrono) {
        total += s.durationSec
        if (s.durationSec >= POLAROID_MIN_SEC) out += Polaroid(s, byId[s.activityId], total)
    }
    return out.asReversed() // 새것부터
}

class AlbumViewModel(private val g: Graph) : ViewModel() {
    val seed: StateFlow<Long> = g.settings.gardenSeed.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    private val daily = g.records.daily().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 공유 카드: 그 사진의 해 격자를 같이 넣는다. */
    fun card(ctx: android.content.Context, p: Polaroid) = ShareCard.render(ctx, p, seed.value,
        YearGrid.build(java.time.LocalDate.parse(p.session.localDate).year, g.weekStart(), daily.value, g.clock.today()))
    val items: StateFlow<List<Polaroid>?> = combine(g.records.allChrono, g.records.allActivities, ::polaroids)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 사진첩: 폴라로이드가 새것부터 두 줄로. 누르면 크게. 공유는 M4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(vm: AlbumViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val seed by vm.seed.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Polaroid?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.album_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { pad ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            Text(stringResource(R.string.album_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            items(list, key = { it.session.id }) { p -> PolaroidCard(p, seed, Modifier.padding(8.dp).clickable { open = p }) }
        }
    }
    val ctx = LocalContext.current
    open?.let { p ->
        Dialog(onDismissRequest = { open = null }) {
            Column {
                PolaroidCard(p, seed, Modifier.fillMaxWidth().clickable { open = null })
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    if (ShareCard.canSave()) FilledTonalButton(onClick = {
                        if (ShareCard.save(ctx, vm.card(ctx, p))) Toast.makeText(ctx, R.string.saved_image, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.save_image)) }
                    Button(onClick = { ShareCard.share(ctx, vm.card(ctx, p)) }) { Text(stringResource(R.string.share)) }
                }
            }
        }
    }
}

/** 흰 테두리 + 아래 넓은 여백에 날짜·활동·길이. 자세·화분·정물은 그 세션이 끝난 때의 모습. */
@Composable
fun PolaroidCard(p: Polaroid, gardenSeed: Long, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dusk = isSystemInDarkTheme()
    val drawing = remember(p, gardenSeed, dusk) {
        SceneArt.build(sceneInput(Garden.of(p.totalAtEndSec, gardenSeed), gardenSeed, Pose.of(p.activity?.iconKey), dusk, square = true))
    }
    val date = java.time.LocalDate.parse(p.session.localDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val caption = stringResource(R.string.polaroid_caption, p.activity?.name ?: "—", Text.duration(ctx, p.session.durationSec.toLong()))
    Column(modifier.shadow(3.dp, RoundedCornerShape(2.dp)).background(Color(0xFFFFFDF8)).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
        SceneCanvas(drawing, "$date, $caption", Modifier.fillMaxWidth().clip(RoundedCornerShape(1.dp)))
        Text(caption, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A342C), modifier = Modifier.padding(top = 8.dp))
        Text(date, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7A7166))
    }
}
