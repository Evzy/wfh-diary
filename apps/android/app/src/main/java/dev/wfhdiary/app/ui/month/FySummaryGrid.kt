package dev.wfhdiary.app.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.model.DiarySummaryDto

@Composable
fun FySummaryGrid(
    fyYear: Int,
    summary: DiarySummaryDto?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.fy_summary_title, fyYear, fyYear + 1),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FySummaryTile(
                label = stringResource(R.string.fy_summary_days_home),
                value = summary?.daysAtHome?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
            FySummaryTile(
                label = stringResource(R.string.fy_summary_days_office),
                value = summary?.daysInOffice?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FySummaryTile(
                label = stringResource(R.string.fy_summary_home_hours),
                value =
                    summary?.let { stringResource(R.string.fy_summary_hours_value, it.homeHours) }
                        ?: "—",
                modifier = Modifier.weight(1f),
            )
            FySummaryTile(
                label = stringResource(R.string.fy_summary_claimable),
                value =
                    summary?.let { stringResource(R.string.fy_summary_money_value, it.claimableWfhAmount) }
                        ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FySummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
            )
        }
    }
}
