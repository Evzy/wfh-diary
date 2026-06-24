package dev.wfhdiary.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class DiaryRepository(
    private val client: HttpClient,
    private val baseUrlProvider: suspend () -> String,
) {
    private suspend fun base(): String = baseUrlProvider()

    suspend fun getDiary(fyYear: Int): DiaryDto =
        client.get("${base()}v1/diaries/$fyYear").body()

    suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntryDto> =
        client.get("${base()}v1/diaries/$fyYear/days") {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()

    suspend fun upsertDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        client.put("${base()}v1/diaries/$fyYear/days/$date") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun createLeave(fyYear: Int, request: CreateLeaveRequest): LeavePeriodDto =
        client.post("${base()}v1/diaries/$fyYear/leave") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getSummary(fyYear: Int): DiarySummaryDto =
        client.get("${base()}v1/diaries/$fyYear/summary").body()

    suspend fun exportXlsxUrl(fyYear: Int): String =
        "${base()}v1/diaries/$fyYear/export.xlsx"

    suspend fun getLeavePeriods(): List<LeavePeriodDto> =
        client.get("${base()}v1/leave-periods").body()

    suspend fun getSettings(): UserSettingsDto =
        client.get("${base()}v1/settings").body()

    suspend fun updateSettings(request: UpdateUserSettingsRequest): UserSettingsDto =
        client.put("${base()}v1/settings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
