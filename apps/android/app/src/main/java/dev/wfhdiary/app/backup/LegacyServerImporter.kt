package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.DiaryRepository
import dev.wfhdiary.app.data.remote.WorkTemplateDto
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/** Phase-1-only bridge from the old API. Fetches everything first; callers restore only on full success. */
class LegacyServerImporter(
    private val remote: DiaryRepository,
    private val appVersion: String,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val firstFyYear: Int = FIRST_FY_YEAR,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun fetch(): BackupDocument {
        val settings = remote.getSettings()
        val leave = remote.getLeavePeriods()
        val templates = remote.getWorkTemplates()
        val currentFy = FinancialYearHelper.currentFyYear(LocalDate.now(clock))
        val days =
            (firstFyYear..currentFy)
                .flatMap { fy -> remote.getDays(fy, null, null) }
                .map { BackupDay(it.date, it.startTime, it.finishTime, it.breakHours, it.description, it.location) }
                .filterNot(DiaryRules::isEmpty)
                .distinctBy { it.date }
                .sortedBy { it.date }
        return BackupDocument(
            exportedAt = OffsetDateTime.now(clock).toString(),
            appVersion = appVersion,
            settings = settings,
            days = days,
            leavePeriods = leave.map { BackupLeavePeriod(it.id, it.startDate, it.endDate, it.note) },
            legacy =
                buildJsonObject {
                    put("workTemplates", json.encodeToJsonElement(ListSerializer(WorkTemplateDto.serializer()), templates))
                },
        )
    }

    companion object {
        const val FIRST_FY_YEAR = 2020
    }
}
