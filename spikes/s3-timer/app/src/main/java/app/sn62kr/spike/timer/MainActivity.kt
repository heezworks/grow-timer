package app.sn62kr.spike.timer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timer.rebase(this, "앱 열림")
        setContent { MaterialTheme { Panel() } }
    }
}

@Composable
private fun Panel() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Timer.log(ctx, if (it) "알림 허용" else "알림 거부: 타이머는 그대로 돈다. 정한 시간 알림은 오지 않는다")
    }
    Column(Modifier.padding(top = 48.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tick.let {
            val s = Timer.load(ctx); val r = Timer.read(ctx)
            val sec = r.activeMs / 1000
            Text(if (s.running) "${s.activity}와 %02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "쉬는 중",
                style = MaterialTheme.typography.headlineMedium)
            if (r.needsCorrection && s.running) Text("타이머가 8시간 넘게 켜져 있었어요. 실제로 보낸 시간을 알려 주세요.")
            if (!Timer.canNotify(ctx)) Text("알림을 켜면 정한 시간이 됐을 때 알려 드려요.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !Timer.canNotify(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                Timer.start(ctx, "기타", 60)
            }) { Text("시작(1분 목표)") }
            Button(onClick = { Timer.finish(ctx) }) { Text("끝내기") }
        }
        Text(ctx.getSharedPreferences("s3", 0).getString("log", "") ?: "", style = MaterialTheme.typography.bodySmall)
    }
}
