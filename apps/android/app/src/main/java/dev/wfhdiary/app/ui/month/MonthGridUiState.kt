package dev.wfhdiary.app.ui.month

import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import java.time.YearMonth

data class MonthGridUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val days: List<DayEntryDto> = emptyList(),
    val summary: DiarySummaryDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val selectedDate: String? = null,
)
