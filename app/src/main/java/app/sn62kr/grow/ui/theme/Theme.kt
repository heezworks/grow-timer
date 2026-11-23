package app.sn62kr.grow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 그림의 팔레트(흰 회벽·바다 파랑·테라코타·올리브)를 화면 색에도 옅게 쓴다. 말로는 하지 않는다(D56).
private val Light = lightColorScheme(
    primary = Color(0xFF2F6DA3), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F4), onPrimaryContainer = Color(0xFF0E3557),
    secondary = Color(0xFFB5552F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DCCF), onSecondaryContainer = Color(0xFF4A1C08),
    tertiary = Color(0xFF5F7334), onTertiary = Color.White,
    background = Color(0xFFFBF8F2), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF8F2), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEEE8DC), onSurfaceVariant = Color(0xFF4D463C),
    surfaceContainer = Color(0xFFF3EEE4), surfaceContainerHigh = Color(0xFFEDE7DC),
    outline = Color(0xFF7F7667),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DC4E8), onPrimary = Color(0xFF0E3557),
    primaryContainer = Color(0xFF214E76), onPrimaryContainer = Color(0xFFD6E6F4),
    secondary = Color(0xFFF0B79D), onSecondary = Color(0xFF4A1C08),
    secondaryContainer = Color(0xFF7A3518), onSecondaryContainer = Color(0xFFF6DCCF),
    tertiary = Color(0xFFBFD28C), onTertiary = Color(0xFF2A3608),
    background = Color(0xFF1B1D1F), onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF1B1D1F), onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF3A3630), onSurfaceVariant = Color(0xFFD0C6B8),
    surfaceContainer = Color(0xFF242628), surfaceContainerHigh = Color(0xFF2E3032),
    outline = Color(0xFF9A9083),
)

@Composable
fun GrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** 활동 색 12가지. 색만으로 뜻을 전하지 않는다 — 언제나 이름과 같이 보인다(D47). */
object ActivityColors {
    val all = listOf(
        Color(0xFF2F6DA3), Color(0xFFC8643B), Color(0xFF6B7F3A), Color(0xFFD9A520),
        Color(0xFF8A73B5), Color(0xFFE07A6A), Color(0xFF2E8C86), Color(0xFF9C7A54),
        Color(0xFF34495E), Color(0xFFB5577A), Color(0xFF5AA0C8), Color(0xFF8FA88A),
    )

    operator fun get(i: Int) = all[Math.floorMod(i, all.size)]
}
