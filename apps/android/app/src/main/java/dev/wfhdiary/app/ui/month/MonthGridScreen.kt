package dev.wfhdiary.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.ui.day.DayEditSheet
import dev.wfhdiary.app.util.DayBadge
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthGridScreen(
    uiState: MonthGridUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDate: (String?) -> Unit,
    onSaveDay: (String, dev.wfhdiary.app.data.remote.UpsertDayEntryRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ym = uiState.yearMonth
    val monthTitle = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.year
    val daysByDate = uiState.days.associateBy { it.date }
    val cells = buildMonthCells(ym, daysByDate)
    val selectedEntry = uiState.selectedDate?.let { daysByDate[it] }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(monthTitle)
                        uiState.summary?.let { s ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(R.string.month_summary_home, s.homeHours),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    stringResource(R.string.month_summary_claimable, s.claimableWfhAmount),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_open_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(ym) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                total > 80f -> onPreviousMonth()
                                total < -80f -> onNextMonth()
                            }
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> total += dragAmount },
                    )
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) {
                items(cells, key = { it.key }) { cell ->
                    MonthDayCell(
                        cell = cell,
                        onClick = { if (cell.inMonth && cell.date != null) onSelectDate(cell.date) },
                    )
                }
            }
            uiState.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (uiState.selectedDate != null) {
        DayEditSheet(
            date = uiState.selectedDate,
            entry = selectedEntry,
            onDismiss = { onSelectDate(null) },
            onSave = { req -> onSaveDay(uiState.selectedDate, req) },
        )
    }
}

private data class MonthCell(
    val key: String,
    val dayNumber: Int?,
    val date: String?,
    val inMonth: Boolean,
    val badge: DayBadge,
)

@Composable
private fun MonthDayCell(cell: MonthCell, onClick: () -> Unit) {
    val bg =
        when (cell.badge) {
            DayBadge.Home -> MaterialTheme.colorScheme.primaryContainer
            DayBadge.Office -> MaterialTheme.colorScheme.secondaryContainer
            DayBadge.Leave -> MaterialTheme.colorScheme.tertiaryContainer
            DayBadge.Empty -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(bg, MaterialTheme.shapes.small)
            .clickable(enabled = cell.inMonth, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayNumber?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.inMonth) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (cell.inMonth) {
                Text(cell.badge.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun buildMonthCells(yearMonth: YearMonth, daysByDate: Map<String, DayEntryDto>): List<MonthCell> {
    val first = yearMonth.atDay(1)
    val startOffset = (first.dayOfWeek.value + 6) % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val result = mutableListOf<MonthCell>()
    for (i in 0 until startOffset) {
        result += MonthCell("pad-$i", null, null, inMonth = false, badge = DayBadge.Empty)
    }
    for (d in 1..daysInMonth) {
        val date = yearMonth.atDay(d)
        val key = date.format(iso)
        val entry = daysByDate[key]
        result += MonthCell(
            key = key,
            dayNumber = d,
            date = key,
            inMonth = true,
            badge = resolveBadge(entry),
        )
    }
    while (result.size % 7 != 0) {
        result += MonthCell("tail-${result.size}", null, null, inMonth = false, badge = DayBadge.Empty)
    }
    return result
}

private fun resolveBadge(entry: DayEntryDto?): DayBadge {
    if (entry == null) return DayBadge.Empty
    if (entry.description.equals("LEAVE", ignoreCase = true)) return DayBadge.Leave
    return when (entry.location?.trim()?.lowercase()) {
        "home" -> DayBadge.Home
        "office" -> DayBadge.Office
        else -> DayBadge.Empty
    }
}
