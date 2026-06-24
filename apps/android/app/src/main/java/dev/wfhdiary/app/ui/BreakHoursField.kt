package dev.wfhdiary.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R

@Composable
fun BreakHoursField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.day_edit_break), modifier = Modifier.weight(1f))
        IconButton(onClick = {
            val v = value.toDoubleOrNull() ?: 0.0
            onValueChange((v - 0.25).coerceAtLeast(0.0).toString())
        }) {
            Text("−")
        }
        Text(value.ifEmpty { "0" }, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = {
            val v = value.toDoubleOrNull() ?: 0.0
            onValueChange((v + 0.25).toString())
        }) {
            Text("+")
        }
    }
}
