package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsSectionTitleRow(
    title: String,
    infoTitle: String? = null,
    infoMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (infoTitle != null && infoMessage != null) {
            SettingsInfoIcon(title = infoTitle, message = infoMessage)
        }
    }
}

@Composable
fun SettingsLabelRow(
    label: String,
    infoTitle: String? = null,
    infoMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (infoTitle != null && infoMessage != null) {
            SettingsInfoIcon(title = infoTitle, message = infoMessage)
        }
    }
}
