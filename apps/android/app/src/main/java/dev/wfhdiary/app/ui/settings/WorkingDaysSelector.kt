package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.util.WorkingDaysMask
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WorkingDaysSelector(
    mask: Int,
    onMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.settings_notification_working_days),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(R.string.settings_notification_working_days_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WorkingDaysMask.allDays().forEach { day ->
            val label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = WorkingDaysMask.isWorkingDay(mask, day),
                onClick = {
                    val enabled = !WorkingDaysMask.isWorkingDay(mask, day)
                    onMaskChange(WorkingDaysMask.setDay(mask, day, enabled))
                },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}
