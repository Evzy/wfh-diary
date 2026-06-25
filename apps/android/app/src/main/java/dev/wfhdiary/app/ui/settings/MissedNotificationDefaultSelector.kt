package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.notification.MissedNotificationDefault

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissedNotificationDefaultSelector(
    selected: String,
    onSelectedChange: (String) -> Unit,
    onOpenGeolocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.settings_missed_notification_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(R.string.settings_missed_notification_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MissedNotificationDefault.options.forEach { option ->
            FilterChip(
                selected = selected.equals(option, ignoreCase = true),
                onClick = { onSelectedChange(option) },
                label = { Text(missedNotificationLabel(option)) },
            )
        }
    }

    if (MissedNotificationDefault.isGeolocation(selected)) {
        Text(
            stringResource(R.string.settings_missed_geolocation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Button(onClick = onOpenGeolocationSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.geolocation_settings_open))
        }
    }
}

@Composable
private fun missedNotificationLabel(option: String): String =
    when (option) {
        MissedNotificationDefault.NONE -> stringResource(R.string.settings_missed_none)
        MissedNotificationDefault.HOME -> stringResource(R.string.notification_action_home)
        MissedNotificationDefault.OFFICE -> stringResource(R.string.notification_action_office)
        MissedNotificationDefault.LEAVE -> stringResource(R.string.notification_action_leave)
        MissedNotificationDefault.GEOLOCATION -> stringResource(R.string.settings_missed_geolocation)
        else -> option
    }
