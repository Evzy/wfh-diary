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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.LeavePeriodDto
import dev.wfhdiary.app.ui.day.DayEditSheet
import dev.wfhdiary.app.ui.leave.LeaveScheduleSheet
import dev.wfhdiary.app.util.DayBadge
import dev.wfhdiary.app.util.LeavePeriodHelper
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HomeDayColor = Color(0xFFA5D6A7)
private val OfficeDayColor = Color(0xFF81D4FA)
private val LeaveDayColor = Color(0xFFE0E0E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthGridScreen(
    uiState: MonthGridUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDate: (String?) -> Unit,
    onSaveDay: (String, dev.wfhdiary.app.data.remote.UpsertDayEntryRequest) -> Unit,
    onOpenLeaveSheet: () -> Unit,
    onDismissLeaveSheet: () -> Unit,
    onScheduleLeave: (dev.wfhdiary.app.data.remote.CreateLeaveRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ym = uiState.yearMonth
    val monthTitle = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.year
    val daysByDate = uiState.days.associateBy { it.date }
    val cells = buildMonthCells(ym, daysByDate, uiState.leavePeriods)
    val selectedEntry = uiState.selectedDate?.let { daysByDate[it] }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(monthTitle) },
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                items(cells, key = { it.key }) { cell ->
                    MonthDayCell(
                        cell = cell,
                        onClick = { if (cell.inMonth && cell.date != null) onSelectDate(cell.date) },
                    )
                }
            }
            Button(
                onClick = onOpenLeaveSheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.schedule_leave_button))
            }
            FySummaryGrid(
                fyYear = uiState.fyYear,
                summary = uiState.summary,
            )
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

    if (uiState.showLeaveSheet) {
        LeaveScheduleSheet(
            onDismiss = onDismissLeaveSheet,
            onSave = onScheduleLeave,
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
            DayBadge.Home -> HomeDayColor
            DayBadge.Office -> OfficeDayColor
            DayBadge.Leave -> LeaveDayColor
            DayBadge.Empty -> MaterialTheme.colorScheme.surface
        }
    val textColor =
        when (cell.badge) {
            DayBadge.Home -> Color(0xFF1B5E20)
            DayBadge.Office -> Color(0xFF01579B)
            DayBadge.Leave -> Color(0xFF616161)
            DayBadge.Empty -> MaterialTheme.colorScheme.onSurface
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
                color = if (cell.inMonth) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cell.inMonth && cell.badge != DayBadge.Empty) {
                Text(
                    cell.badge.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                )
            }
        }
    }
}

private fun buildMonthCells(
    yearMonth: YearMonth,
    daysByDate: Map<String, DayEntryDto>,
    leavePeriods: List<LeavePeriodDto>,
): List<MonthCell> {
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
        val onLeave = LeavePeriodHelper.isOnLeave(date, leavePeriods)
        result += MonthCell(
            key = key,
            dayNumber = d,
            date = key,
            inMonth = true,
            badge = resolveBadge(entry, onLeave),
        )
    }
    while (result.size % 7 != 0) {
        result += MonthCell("tail-${result.size}", null, null, inMonth = false, badge = DayBadge.Empty)
    }
    return result
}

private fun resolveBadge(entry: DayEntryDto?, onLeave: Boolean): DayBadge {
    if (onLeave) return DayBadge.Leave
    if (entry == null) return DayBadge.Empty
    if (entry.description.equals("LEAVE", ignoreCase = true)) return DayBadge.Leave
    return when (entry.location?.trim()?.lowercase()) {
        "home" -> DayBadge.Home
        "office" -> DayBadge.Office
        else -> DayBadge.Empty
    }
}
