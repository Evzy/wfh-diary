package dev.wfhdiary.app.ui.month

import dev.wfhdiary.app.data.model.DayEntryDto
import dev.wfhdiary.app.data.model.DiarySummaryDto
import dev.wfhdiary.app.data.model.LeavePeriodDto
import dev.wfhdiary.app.util.FinancialYearHelper
import java.time.YearMonth

data class MonthGridUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val fyYear: Int = FinancialYearHelper.currentFyYear(java.time.LocalDate.now()),
    val days: List<DayEntryDto> = emptyList(),
    val leavePeriods: List<LeavePeriodDto> = emptyList(),
    val summary: DiarySummaryDto? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selectedDate: String? = null,
    val showLeaveSheet: Boolean = false,
)
