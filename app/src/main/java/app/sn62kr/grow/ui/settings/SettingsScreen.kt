package app.sn62kr.grow.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.BuildConfig
import app.sn62kr.grow.Graph
import app.sn62kr.grow.R
import app.sn62kr.grow.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class SettingsViewModel(private val g: Graph) : ViewModel() {
    private val eager = SharingStarted.Eagerly
    val notify: StateFlow<Boolean> = g.settings.notify.stateIn(viewModelScope, eager, true)
    val weekStart: StateFlow<Int> = g.settings.weekStart.stateIn(viewModelScope, eager, 0)
    val lastBackup: StateFlow<Long> = g.settings.lastBackupAt.stateIn(viewModelScope, eager, 0L)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun setNotify(v: Boolean) = viewModelScope.launch { g.settings.setNotify(v); g.rebaseTimer() } // 도는 타이머의 알림을 바로 맞춘다
    fun setWeekStart(v: Int) = viewModelScope.launch { g.settings.setWeekStart(v); app.sn62kr.grow.widget.WidgetUpdates.all(g.appContext) }

    fun export(ctx: android.content.Context, uri: Uri) = viewModelScope.launch {
        val text = g.exportBackup()
        val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess }
        if (ok) { g.settings.setLastBackup(g.clock.now().wall); messages.emit(ctx.getString(R.string.set_exported)) }
        else messages.emit(ctx.getString(R.string.import_broken))
    }

    fun import(ctx: android.content.Context, uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
        val msg = when (val r = if (text == null) Backup.Read.Broken else Backup.decode(text)) {
            is Backup.Read.Ok -> g.importBackup(r).let { ctx.getString(R.string.set_imported, it.activitiesAdded, it.sessionsAdded, it.skipped) }
            Backup.Read.NotOurs -> ctx.getString(R.string.import_not_ours, ctx.getString(R.string.app_name))
            Backup.Read.Newer -> ctx.getString(R.string.import_newer)
            Backup.Read.Broken -> ctx.getString(R.string.import_broken)
        }
        messages.emit(msg)
    }

    fun deleteAll(ctx: android.content.Context) = viewModelScope.launch {
        g.timer.finish(null) // 도는 타이머도 버린다
        g.records.deleteAll()
        messages.emit(ctx.getString(R.string.delete_all_done))
    }
}

/** 설정(spec 2-8). 테마는 시스템을 따른다(D60). 처리방침 링크는 도메인을 산 뒤(Q21). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onActivities: () -> Unit, onLicenses: () -> Unit) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val notify by vm.notify.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    var pickWeek by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) }
    val today = java.time.LocalDate.now()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(ctx, it) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(ctx, it) } }
    val fileName = "${ctx.getString(R.string.app_name)}-$today.json"
    val hasPermission = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Row(stringResource(R.string.set_activities), onClick = onActivities)
            HorizontalDivider()

            Header(R.string.set_backup)
            Row(stringResource(R.string.set_export), if (last > 0) stringResource(R.string.set_last_backup,
                Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)))
                else stringResource(R.string.set_never_backup)) { exportLauncher.launch(fileName) }
            Row(stringResource(R.string.set_import)) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            Note(R.string.set_backup_note)
            HorizontalDivider()

            Header(R.string.set_notify)
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_notify)) },
                supportingContent = { Text(stringResource(R.string.set_notify_note)) },
                trailingContent = { Switch(checked = notify, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.selectable(notify, role = Role.Switch) { vm.setNotify(!notify) },
            )
            if (notify && !hasPermission) Row(stringResource(R.string.set_notify_system)) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            HorizontalDivider()

            Row(stringResource(R.string.set_week_start), weekLabel(weekStart)) { pickWeek = true }
            HorizontalDivider()

            Note(R.string.set_privacy)
            Row(stringResource(R.string.set_licenses), onClick = onLicenses)
            Row(stringResource(R.string.set_feedback)) {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.feedback_subject) + " · " + BuildConfig.VERSION_NAME)
                    .putExtra(Intent.EXTRA_TEXT, "\n\n—\n${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${BuildConfig.VERSION_NAME}")
                runCatching { ctx.startActivity(mail) }
            }
            ListItem(headlineContent = { Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
            HorizontalDivider()
            Row(stringResource(R.string.set_delete_all), danger = true) { deleteStep = 1 }
        }
    }

    if (pickWeek) AlertDialog(
        onDismissRequest = { pickWeek = false },
        title = { Text(stringResource(R.string.set_week_start)) },
        text = {
            Column {
                listOf(0, 7, 1, 6).forEach { v ->
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().selectable(weekStart == v, role = Role.RadioButton) { vm.setWeekStart(v); pickWeek = false }.padding(vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = weekStart == v, onClick = null)
                        Text(weekLabel(v), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickWeek = false }) { Text(stringResource(R.string.cancel)) } },
    )
    when (deleteStep) {
        1 -> AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            text = { Text(stringResource(R.string.delete_all_ask)) },
            confirmButton = { TextButton(onClick = { deleteStep = 0; exportLauncher.launch(fileName) }) { Text(stringResource(R.string.delete_all_backup)) } },
            dismissButton = { TextButton(onClick = { deleteStep = 2 }) { Text(stringResource(R.string.delete_all_go), color = MaterialTheme.colorScheme.error) } },
        )
        2 -> AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = { TextButton(onClick = { deleteStep = 0; vm.deleteAll(ctx) }) { Text(stringResource(R.string.set_delete_all), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 문제 보내기 받는 곳: 게시 명의의 연락처(D69). 앱은 아무것도 자동으로 보내지 않는다 — 사용자가 메일 앱에서 직접 보낸다(Q26). */
private const val FEEDBACK_EMAIL = "heez.works@gmail.com"

@Composable
private fun weekLabel(v: Int): String = if (v in 1..7) DayOfWeek.of(v).getDisplayName(TextStyle.FULL, Locale.getDefault()) else stringResource(R.string.week_locale)

@Composable
private fun Header(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() })

@Composable
private fun Note(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
private fun Row(title: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
    supportingContent = sub?.let { { Text(it) } },
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    modifier = Modifier.clickable(onClick = onClick),
)
