package app.sn62kr.grow.ui.plant

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import app.sn62kr.grow.core.garden.GardenState
import app.sn62kr.grow.core.garden.Species
import app.sn62kr.grow.ui.PlantView
import app.sn62kr.grow.ui.Text
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@StringRes
fun Species.label(): Int = when (this) {
    Species.OLIVE -> R.string.species_olive
    Species.LEMON -> R.string.species_lemon
    Species.LAVENDER -> R.string.species_lavender
    Species.GERANIUM -> R.string.species_geranium
}

class PlantViewModel(g: Graph) : ViewModel() {
    val garden: StateFlow<GardenState?> = g.garden.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 식물 화면(plan M3): 지금 화분 + 다음 모습까지 남은 시간 + 다 자란 화분들. 죽지 않고, 쉬면 그대로 기다린다(D23). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantScreen(vm: PlantViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val gs by vm.garden.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { pad ->
        val g = gs ?: return@Scaffold
        val p = g.current
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val name = stringResource(p.species.label())
            PlantView(p, stringResource(R.string.plant_desc, name, p.stage), Modifier.fillMaxWidth(0.62f))
            Text(stringResource(R.string.plant_title, p.order + 1, name), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            if (p.spentSec <= 0) Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            else {
                Text(stringResource(R.string.plant_with, Text.duration(ctx, p.spentSec)), style = MaterialTheme.typography.bodyLarge)
                if (p.toNextStageSec > 0) Text(stringResource(R.string.plant_next, Text.duration(ctx, p.toNextStageSec)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.still_next, Text.duration(ctx, g.toNextStillLifeSec)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            if (g.grown.isNotEmpty()) {
                Text(stringResource(R.string.plant_grown), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp).semantics { heading() })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    g.grown.reversed().forEach { q ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val n = stringResource(q.species.label())
                            PlantView(q, n, Modifier.width(88.dp))
                            Text(stringResource(R.string.plant_title, q.order + 1, n), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
