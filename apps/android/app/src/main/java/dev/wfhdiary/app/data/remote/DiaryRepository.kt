package dev.wfhdiary.app.data.remote

import dev.wfhdiary.app.data.DiaryDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType

class DiaryRepository(
    private val client: HttpClient,
    private val baseUrlProvider: suspend () -> String,
) : DiaryDataSource {
    private suspend fun base(): String = baseUrlProvider()

    override suspend fun getDiary(fyYear: Int): DiaryDto =
        client.get("${base()}v1/diaries/$fyYear").body()

    override suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntryDto> =
        client.get("${base()}v1/diaries/$fyYear/days") {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()

    override suspend fun upsertDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        client.put("${base()}v1/diaries/$fyYear/days/$date") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Server API has no full-replace endpoint; delegates to partial upsert (importer-only; removed with backend). */
    override suspend fun replaceDay(fyYear: Int, date: String, request: UpsertDayEntryRequest): DayEntryDto =
        upsertDay(fyYear, date, request)

    override suspend fun deleteDay(fyYear: Int, date: String): DayEntryDto =
        client.delete("${base()}v1/diaries/$fyYear/days/$date").body()

    override suspend fun createLeave(fyYear: Int, request: CreateLeaveRequest): LeavePeriodDto =
        client.post("${base()}v1/diaries/$fyYear/leave") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun getSummary(fyYear: Int): DiarySummaryDto =
        client.get("${base()}v1/diaries/$fyYear/summary").body()

    override suspend fun downloadExportXlsx(fyYear: Int): Pair<ByteArray, String> {
        val response = client.get("${base()}v1/diaries/$fyYear/export.xlsx")
        val bytes = response.readRawBytes()
        val filename = "WFH-Diary-$fyYear-${fyYear + 1}.xlsx"
        return bytes to filename
    }

    override suspend fun getLeavePeriods(): List<LeavePeriodDto> =
        client.get("${base()}v1/leave-periods").body()

    override suspend fun deleteLeave(id: String) {
        client.delete("${base()}v1/leave-periods/$id")
    }

    override suspend fun getSettings(): UserSettingsDto =
        client.get("${base()}v1/settings").body()

    override suspend fun updateSettings(request: UpdateUserSettingsRequest): UserSettingsDto =
        client.put("${base()}v1/settings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getWorkTemplates(): List<WorkTemplateDto> =
        client.get("${base()}v1/work-templates").body()

    suspend fun createWorkTemplate(request: CreateWorkTemplateRequest): WorkTemplateDto =
        client.post("${base()}v1/work-templates") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteWorkTemplate(id: String) {
        client.delete("${base()}v1/work-templates/$id")
    }
}
