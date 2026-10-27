package dev.wfhdiary.app.ui.month

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.LeavePeriodHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MonthGridViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WfhDiaryApplication
    private val repo = app.diaryRepository

    private val _uiState = MutableStateFlow(MonthGridUiState())
    val uiState = _uiState.asStateFlow()

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val daysCache = mutableMapOf<String, DayEntryDto>()
    private val summaryByFy = mutableMapOf<Int, DiarySummaryDto>()
    private var refreshInFlight = false

    init {
        viewModelScope.launch {
            val now = YearMonth.now()
            applyMonth(now)
            runCatching {
                val leavePeriods = repo.getLeavePeriods()
                _uiState.update { it.copy(leavePeriods = leavePeriods) }
            }
        }
    }

    fun refreshFromRemote(showProgress: Boolean = false) {
        if (refreshInFlight) return
        viewModelScope.launch {
            refreshInFlight = true
            val state = _uiState.value
            val fyYear = state.fyYear
            val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
            _uiState.update {
                it.copy(
                    refreshing = showProgress || it.refreshing,
                    error = null,
                )
            }
            runCatching {
                repo.getDiary(fyYear)
                val days = repo.getDays(fyYear, fyStart.format(isoDate), fyEnd.format(isoDate))
                daysCache.keys.removeAll { it >= fyStart.format(isoDate) && it <= fyEnd.format(isoDate) }
                days.forEach { daysCache[it.date] = it }
                val leavePeriods = repo.getLeavePeriods()
                val summary = repo.getSummary(fyYear)
                summaryByFy[fyYear] = summary
                _uiState.update {
                    it.copy(
                        days = daysForMonth(state.yearMonth),
                        leavePeriods = leavePeriods,
                        summary = summary,
                        refreshing = false,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        refreshing = false,
                        error = e.message ?: "Failed to refresh",
                    )
                }
            }
            refreshInFlight = false
        }
    }

    fun showPreviousMonth() {
        applyMonth(_uiState.value.yearMonth.minusMonths(1))
    }

    fun showNextMonth() {
        applyMonth(_uiState.value.yearMonth.plusMonths(1))
    }

    fun showPreviousYear() {
        applyMonth(_uiState.value.yearMonth.minusMonths(12))
    }

    fun showNextYear() {
        applyMonth(_uiState.value.yearMonth.plusMonths(12))
    }

    fun selectDate(date: String?) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun openLeaveSheet() {
        _uiState.update { it.copy(showLeaveSheet = true) }
    }

    fun dismissLeaveSheet() {
        _uiState.update { it.copy(showLeaveSheet = false) }
    }

    fun scheduleLeave(request: CreateLeaveRequest) {
        viewModelScope.launch {
            refreshAfterLeaveChange(closeSheet = true) {
                repo.createLeave(_uiState.value.fyYear, request)
            }
        }
    }

    fun deleteLeave(id: String) {
        viewModelScope.launch {
            refreshAfterLeaveChange(closeSheet = false, closeSelectedDay = true) {
                repo.deleteLeave(id)
            }
        }
    }

    fun removeLeaveForDate(date: String) {
        val localDate = LocalDate.parse(date, isoDate)
        val period = LeavePeriodHelper.coveringPeriod(localDate, _uiState.value.leavePeriods) ?: return
        deleteLeave(period.id)
    }

    private suspend fun refreshAfterLeaveChange(
        closeSheet: Boolean,
        closeSelectedDay: Boolean = false,
        action: suspend () -> Unit,
    ) {
        val fyYear = _uiState.value.fyYear
        runCatching {
            action()
            val leavePeriods = repo.getLeavePeriods()
            val summary = repo.getSummary(fyYear)
            summaryByFy[fyYear] = summary
            _uiState.update {
                it.copy(
                    leavePeriods = leavePeriods,
                    summary = summary,
                    showLeaveSheet = if (closeSheet) false else it.showLeaveSheet,
                    selectedDate = if (closeSelectedDay) null else it.selectedDate,
                    error = null,
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    fun saveDay(date: String, request: UpsertDayEntryRequest) {
        viewModelScope.launch {
            val localDate = LocalDate.parse(date, isoDate)
            val fyYear = FinancialYearHelper.currentFyYear(localDate)
            runCatching {
                val saved = repo.replaceDay(fyYear, date, request)
                refreshDayInState(fyYear, saved, date)
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteDay(date: String) {
        viewModelScope.launch {
            val localDate = LocalDate.parse(date, isoDate)
            val fyYear = FinancialYearHelper.currentFyYear(localDate)
            runCatching {
                val cleared = repo.deleteDay(fyYear, date)
                refreshDayInState(fyYear, cleared, date)
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun refreshDayInState(fyYear: Int, saved: DayEntryDto, date: String) {
        daysCache[date] = saved
        val summary = repo.getSummary(fyYear)
        summaryByFy[fyYear] = summary
        _uiState.update {
            it.copy(
                days = daysForMonth(it.yearMonth),
                summary = if (it.fyYear == fyYear) summary else it.summary,
                selectedDate = null,
            )
        }
    }

    private fun applyMonth(yearMonth: YearMonth) {
        val fyYear = FinancialYearHelper.currentFyYear(yearMonth.atDay(15))
        _uiState.update {
            it.copy(
                yearMonth = yearMonth,
                fyYear = fyYear,
                days = daysForMonth(yearMonth),
                summary = summaryByFy[fyYear],
                error = null,
            )
        }
        viewModelScope.launch {
            val from = yearMonth.atDay(1).format(isoDate)
            val to = yearMonth.atEndOfMonth().format(isoDate)
            val monthDays =
                runCatching {
                    FinancialYearHelper.fyYearsCovering(yearMonth).flatMap { fy -> repo.getDays(fy, from, to) }
                }.getOrNull() ?: return@launch
            daysCache.keys.removeAll { it in from..to }
            monthDays.forEach { daysCache[it.date] = it }
            val summary = runCatching { repo.getSummary(fyYear) }.getOrNull()
            if (summary != null) summaryByFy[fyYear] = summary
            _uiState.update { state ->
                if (state.yearMonth == yearMonth) {
                    state.copy(days = daysForMonth(yearMonth), summary = summaryByFy[fyYear] ?: state.summary)
                } else {
                    state
                }
            }
        }
    }

    private fun daysForMonth(yearMonth: YearMonth): List<DayEntryDto> {
        val from = yearMonth.atDay(1)
        val to = yearMonth.atEndOfMonth()
        return daysCache.values
            .filter { entry ->
                val date = LocalDate.parse(entry.date, isoDate)
                !date.isBefore(from) && !date.isAfter(to)
            }
            .sortedBy { it.date }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MonthGridViewModel(application) as T
            }
    }
}
