package dev.wfhdiary.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R

@Composable
fun WorkPresetsSection(
    form: SettingsFormState,
    onCustomizeHome: () -> Unit,
    onCustomizeOffice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionTitleRow(
            title = stringResource(R.string.work_presets_section_title),
            infoTitle = stringResource(R.string.settings_info_presets_title),
            infoMessage = stringResource(R.string.settings_info_presets_message),
        )
        Text(
            stringResource(R.string.work_presets_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(stringResource(R.string.settings_default_home), style = MaterialTheme.typography.labelLarge)
        WorkScheduleSummaryRow(
            label = stringResource(R.string.work_schedule_times_label),
            start = form.defaultHomeStart,
            finish = form.defaultHomeFinish,
            breakHours = form.defaultHomeBreakHours,
            description = form.defaultHomeDescription,
            onCustomize = onCustomizeHome,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.settings_default_office), style = MaterialTheme.typography.labelLarge)
        WorkScheduleSummaryRow(
            label = stringResource(R.string.work_schedule_times_label),
            start = form.defaultOfficeStart,
            finish = form.defaultOfficeFinish,
            breakHours = form.defaultOfficeBreakHours,
            description = form.defaultOfficeDescription,
            onCustomize = onCustomizeOffice,
        )
    }
}
