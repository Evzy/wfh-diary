package dev.wfhdiary.app.data

import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.DayEntryDto
import dev.wfhdiary.app.data.remote.DiaryDto
import dev.wfhdiary.app.data.remote.DiarySummaryDto
import dev.wfhdiary.app.data.remote.LeavePeriodDto
import dev.wfhdiary.app.data.remote.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.data.remote.UserSettingsDto

interface DiaryDataSource {
    suspend fun getDiary(fyYear: Int): DiaryDto

    suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntryDto>

    /** Partial update: null fields are left unchanged. */
    suspend fun upsertDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto

    /** Full replace: null or blank fields are cleared. */
    suspend fun replaceDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto

    suspend fun deleteDay(fyYear: Int, date: String): DayEntryDto

    suspend fun createLeave(fyYear: Int, request: CreateLeaveRequest): LeavePeriodDto

    suspend fun getSummary(fyYear: Int): DiarySummaryDto

    suspend fun downloadExportXlsx(fyYear: Int): Pair<ByteArray, String>

    suspend fun getLeavePeriods(): List<LeavePeriodDto>

    suspend fun deleteLeave(id: String)

    suspend fun getSettings(): UserSettingsDto

    suspend fun updateSettings(request: UpdateUserSettingsRequest): UserSettingsDto
}
