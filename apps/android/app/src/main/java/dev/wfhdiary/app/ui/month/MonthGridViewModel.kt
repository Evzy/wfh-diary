package dev.wfhdiary.app.ui.month

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.local.DayEntryEntity
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.util.FinancialYearHelper
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
    private val dao = app.database.dayEntryDao()

    private val _uiState = MutableStateFlow(MonthGridUiState())
    val uiState = _uiState.asStateFlow()

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadMonth(YearMonth.now())
    }

    fun loadMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val from = yearMonth.atDay(1)
            val to = yearMonth.atEndOfMonth()
            val fyYear = FinancialYearHelper.currentFyYear(yearMonth.atDay(15))
            _uiState.update { it.copy(yearMonth = yearMonth, fyYear = fyYear, loading = true, error = null) }
            runCatching {
                repo.getDiary(fyYear)
                val days = repo.getDays(fyYear, from.format(isoDate), to.format(isoDate))
                val leavePeriods = repo.getLeavePeriods()
                val summary = repo.getSummary(fyYear)
                cacheDays(days, fyYear)
                _uiState.update {
                    it.copy(
                        days = days,
                        leavePeriods = leavePeriods,
                        summary = summary,
                        loading = false,
                        error = null,
                    )
                }
            }.onFailure { e ->
                val cached = loadCached(from, to)
                _uiState.update {
                    it.copy(
                        days = cached,
                        loading = false,
                        error = e.message ?: "Failed to load month",
                    )
                }
            }
        }
    }

    fun showPreviousMonth() {
        loadMonth(_uiState.value.yearMonth.minusMonths(1))
    }

    fun showNextMonth() {
        loadMonth(_uiState.value.yearMonth.plusMonths(1))
    }

    fun showPreviousYear() {
        loadMonth(_uiState.value.yearMonth.minusMonths(12))
    }

    fun showNextYear() {
        loadMonth(_uiState.value.yearMonth.plusMonths(12))
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
            refreshAfterLeaveChange(closeSheet = false) {
                repo.deleteLeave(id)
            }
        }
    }

    private suspend fun refreshAfterLeaveChange(closeSheet: Boolean, action: suspend () -> Unit) {
        val state = _uiState.value
        val fyYear = state.fyYear
        val from = state.yearMonth.atDay(1)
        val to = state.yearMonth.atEndOfMonth()
        runCatching {
            action()
            val leavePeriods = repo.getLeavePeriods()
            val days = repo.getDays(fyYear, from.format(isoDate), to.format(isoDate))
            val summary = repo.getSummary(fyYear)
            cacheDays(days, fyYear)
            _uiState.update {
                it.copy(
                    days = days,
                    leavePeriods = leavePeriods,
                    summary = summary,
                    showLeaveSheet = if (closeSheet) false else it.showLeaveSheet,
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
                val saved = repo.upsertDay(fyYear, date, request)
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
        cacheDays(listOf(saved), fyYear)
        val summary = repo.getSummary(fyYear)
        val days =
            _uiState.value.days.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.date == date }
                if (idx >= 0) list[idx] = saved else list.add(saved)
            }.sortedBy { it.date }
        _uiState.update { it.copy(days = days, summary = summary, selectedDate = null) }
    }

    private suspend fun cacheDays(days: List<DayEntryDto>, fyYear: Int) {
        val now = System.currentTimeMillis()
        dao.upsertAll(
            days.map { dto ->
                DayEntryEntity(
                    date = dto.date,
                    fyYear = fyYear,
                    dayName = dto.dayName,
                    startTime = dto.startTime,
                    finishTime = dto.finishTime,
                    breakHours = dto.breakHours,
                    totalHours = dto.totalHours,
                    description = dto.description,
                    location = dto.location,
                    cachedAtMillis = now,
                )
            },
        )
    }

    private suspend fun loadCached(from: LocalDate, to: LocalDate): List<DayEntryDto> =
        dao.getBetween(from.format(isoDate), to.format(isoDate)).map { e ->
            DayEntryDto(
                date = e.date,
                dayName = e.dayName,
                startTime = e.startTime,
                finishTime = e.finishTime,
                breakHours = e.breakHours,
                totalHours = e.totalHours,
                description = e.description,
                location = e.location,
            )
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
