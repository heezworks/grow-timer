package app.sn62kr.grow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sn62kr.grow.R
import app.sn62kr.grow.ui.theme.ActivityColors

@Composable
fun ColorDot(colorIndex: Int, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(ActivityColors[colorIndex]))
}

/** 시간·분 두 칸. 숫자만 받는다. */
@Composable
fun DurationFields(hours: String, minutes: String, onHours: (String) -> Unit, onMinutes: (String) -> Unit, modifier: Modifier = Modifier) {
    val digits = KeyboardOptions(keyboardType = KeyboardType.Number)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(hours, { v -> onHours(v.filter(Char::isDigit).take(2)) }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.manual_hours)) }, singleLine = true, keyboardOptions = digits)
        OutlinedTextField(minutes, { v -> onMinutes(v.filter(Char::isDigit).take(3)) }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.manual_minutes)) }, singleLine = true, keyboardOptions = digits)
    }
}

fun fieldsToSec(hours: String, minutes: String): Int = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60
