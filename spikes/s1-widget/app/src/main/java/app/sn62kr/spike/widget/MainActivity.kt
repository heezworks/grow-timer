package app.sn62kr.spike.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 스파이크 조종판: 오늘 시간 더하기 → 위젯 반영 시간 재기, 자정 알람 시험.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DayChangeReceiver.scheduleNextMidnight(this)
        setContent { MaterialTheme { Panel() } }
    }
}

private fun fmt(ms: Long) = if (ms == 0L) "-" else SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA).format(Date(ms))

@androidx.compose.runtime.Composable
private fun Panel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("S1 위젯 스파이크", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = {
            Store.addToday(ctx, 30)
            scope.launch { DayChangeReceiver.updateAllWidgets(ctx); status = "갱신 요청 끝" }
        }) { Text("오늘 +30분 → 위젯 갱신") }
        Button(onClick = {
            Store.regenerate(ctx, seed = (System.currentTimeMillis() % 1000).toInt())
            scope.launch { DayChangeReceiver.updateAllWidgets(ctx); status = "새 1년 데이터" }
        }) { Text("가짜 1년 다시 만들기") }
        Button(onClick = {
            val am = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
            am.requestPinAppWidget(android.content.ComponentName(ctx, YearWidgetReceiver::class.java), null, null)
        }) { Text("홈에 올해 격자(4×2) 추가") }
        Button(onClick = {
            val am = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
            am.requestPinAppWidget(android.content.ComponentName(ctx, RecentWidgetReceiver::class.java), null, null)
        }) { Text("홈에 최근 격자(4×1) 추가") }
        Button(onClick = {
            DayChangeReceiver.scheduleAt(ctx, System.currentTimeMillis() + 60_000)
            status = "1분 뒤 알람을 걸었다(자정 경로 시험)"
        }) { Text("자정 알람 경로: 1분 뒤 시험") }

        tick.let {
            val saved = Store.long(ctx, "savedAt")
            val rendered = Store.long(ctx, "renderedAt")
            Text("저장: ${fmt(saved)}\n위젯 그림: ${fmt(rendered)}" +
                (if (saved in 1..rendered) "\n반영까지 ${rendered - saved}ms" else ""))
            Text(Store.str(ctx, "renderInfo"))
            Text("알람 예약: ${fmt(Store.long(ctx, "midnightScheduledFor"))}\n" +
                "알람 실행: ${fmt(Store.long(ctx, "midnightFiredAt"))} ${Store.str(ctx, "midnightAction")}")
            Text(status)
        }
    }
}
