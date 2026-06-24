package dev.wfhdiary.app.ui.month

import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.LeavePeriodDto
import dev.wfhdiary.app.util.FinancialYearHelper
import java.time.YearMonth

data class MonthGridUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val fyYear: Int = FinancialYearHelper.currentFyYear(java.time.LocalDate.now()),
    val days: List<DayEntryDto> = emptyList(),
    val leavePeriods: List<LeavePeriodDto> = emptyList(),
    val summary: DiarySummaryDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val selectedDate: String? = null,
    val showLeaveSheet: Boolean = false,
)
