package dev.wfhdiary.app.ui.month

import dev.wfhdiary.app.data.model.DayEntry
import dev.wfhdiary.app.data.model.DiarySummary
import dev.wfhdiary.app.data.model.LeavePeriod
import dev.wfhdiary.app.util.FinancialYearHelper
import java.time.YearMonth

data class MonthGridUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val fyYear: Int = FinancialYearHelper.currentFyYear(java.time.LocalDate.now()),
    val days: List<DayEntry> = emptyList(),
    val leavePeriods: List<LeavePeriod> = emptyList(),
    val summary: DiarySummary? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selectedDate: String? = null,
    val showLeaveSheet: Boolean = false,
)
