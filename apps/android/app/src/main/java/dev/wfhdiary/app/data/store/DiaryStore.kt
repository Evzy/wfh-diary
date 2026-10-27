package dev.wfhdiary.app.data.store

import androidx.room.withTransaction
import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.backup.BackupDocument
import dev.wfhdiary.app.backup.BackupLeavePeriod
import dev.wfhdiary.app.data.local.DayEntryEntity
import dev.wfhdiary.app.data.local.LeavePeriodEntity
import dev.wfhdiary.app.data.local.SettingsEntity
import dev.wfhdiary.app.data.local.WfhDiaryDatabase
import dev.wfhdiary.app.data.model.DayEntry
import dev.wfhdiary.app.data.model.DayEntryUpdate
import dev.wfhdiary.app.data.model.DiaryRange
import dev.wfhdiary.app.data.model.DiarySummary
import dev.wfhdiary.app.data.model.LeavePeriod
import dev.wfhdiary.app.data.model.NewLeave
import dev.wfhdiary.app.data.model.UserSettings
import dev.wfhdiary.app.data.model.UserSettingsUpdate
import dev.wfhdiary.app.export.XlsxWriter
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class DiaryStore(
    private val db: WfhDiaryDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dayDao = db.dayEntryDao()
    private val leaveDao = db.leavePeriodDao()
    private val settingsDao = db.settingsDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits after every committed write; drives automatic backups. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = db.withTransaction { block() }
        _changes.tryEmit(Unit)
        return result
    }

    suspend fun getDiary(fyYear: Int): DiaryRange {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        return DiaryRange(fyYear, start.toString(), end.toString())
    }

    suspend fun getDays(fyYear: Int, from: String?, to: String?): List<DayEntry> {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val lower = maxOf(fyStart, from?.let(::parseDate) ?: fyStart)
        val upper = minOf(fyEnd, to?.let(::parseDate) ?: fyEnd)
        if (lower > upper) return emptyList()
        return dayDao.getBetween(lower.toString(), upper.toString()).map { DiaryRules.toDayEntry(it.toBackupDay()) }
    }

    /** Partial update: null fields are left unchanged. */
    suspend fun upsertDay(fyYear: Int, date: String, request: DayEntryUpdate): DayEntry =
        write {
            requireInFy(fyYear, date)
            save(DiaryRules.applyUpsert(dayDao.get(date)?.toBackupDay(), date, request))
        }

    /** Full replace: null or blank fields are cleared. */
    suspend fun replaceDay(fyYear: Int, date: String, request: DayEntryUpdate): DayEntry =
        write {
            requireInFy(fyYear, date)
            save(DiaryRules.replace(date, request))
        }

    suspend fun deleteDay(fyYear: Int, date: String): DayEntry =
        write {
            requireInFy(fyYear, date)
            dayDao.delete(date)
            DiaryRules.toDayEntry(BackupDay(date))
        }

    suspend fun createLeave(fyYear: Int, request: NewLeave): LeavePeriod =
        write {
            var start = parseDate(request.startDate)
            var end = parseDate(request.endDate)
            if (end < start) start = end.also { end = start }
            val period =
                LeavePeriodEntity(
                    id = UUID.randomUUID().toString(),
                    startDate = start.toString(),
                    endDate = end.toString(),
                    note = request.note?.trim()?.ifEmpty { null },
                    createdAtMillis = clock(),
                )
            leaveDao.insert(period)
            DiaryRules.leaveDatesInFy(fyYear, start, end).forEach { save(DiaryRules.leaveDay(it.toString())) }
            period.toLeavePeriod()
        }

    suspend fun getSummary(fyYear: Int): DiarySummary =
        DiaryRules.summarize(daysInFy(fyYear))

    suspend fun downloadExportXlsx(fyYear: Int): Pair<ByteArray, String> {
        val bytes = XlsxWriter.write(fyYear, getSettings().displayName, daysInFy(fyYear))
        return bytes to XlsxWriter.filename(fyYear)
    }

    suspend fun getLeavePeriods(): List<LeavePeriod> = leaveDao.getAll().map { it.toLeavePeriod() }

    suspend fun deleteLeave(id: String) {
        write {
            val period = leaveDao.get(id) ?: return@write
            leaveDao.delete(id)
            val others = leaveDao.getAll().map { parseDate(it.startDate)..parseDate(it.endDate) }
            val removed = parseDate(period.startDate)..parseDate(period.endDate)
            for (date in DiaryRules.datesToClearOnLeaveDelete(removed, others)) {
                val existing = dayDao.get(date.toString()) ?: continue
                if (existing.description.equals(DiaryRules.LEAVE, ignoreCase = true)) {
                    dayDao.delete(existing.date)
                }
            }
        }
    }

    suspend fun getSettings(): UserSettings =
        settingsDao.get()?.let { json.decodeFromString(UserSettings.serializer(), it.settingsJson) }
            ?: DiaryRules.defaultSettings()

    suspend fun updateSettings(request: UserSettingsUpdate): UserSettings =
        write {
            val updated = DiaryRules.applySettingsUpdate(getSettings(), request)
            settingsDao.upsert(
                SettingsEntity(
                    settingsJson = json.encodeToString(UserSettings.serializer(), updated),
                    legacyJson = settingsDao.get()?.legacyJson,
                ),
            )
            updated
        }

    suspend fun exportDocument(appVersion: String, exportedAt: String): BackupDocument =
        db.withTransaction {
            BackupDocument(
                exportedAt = exportedAt,
                appVersion = appVersion,
                settings = getSettings(),
                days = dayDao.getAll().map { it.toBackupDay() },
                leavePeriods = leaveDao.getAll().map { BackupLeavePeriod(it.id, it.startDate, it.endDate, it.note) },
                legacy =
                    settingsDao.get()?.legacyJson?.let { json.parseToJsonElement(it).jsonObject }
                        ?: JsonObject(emptyMap()),
            )
        }

    suspend fun localCounts(): LocalCounts = LocalCounts(dayDao.count(), leaveDao.count())

    /** Replaces every table with [document] in one transaction. Callers validate first. */
    suspend fun replaceAll(document: BackupDocument) {
        write {
            val now = clock()
            dayDao.deleteAll()
            leaveDao.deleteAll()
            settingsDao.deleteAll()
            dayDao.upsertAll(document.days.filterNot(DiaryRules::isEmpty).map { it.toEntity(now) })
            leaveDao.insertAll(
                document.leavePeriods.map { LeavePeriodEntity(it.id, it.startDate, it.endDate, it.note, now) },
            )
            settingsDao.upsert(
                SettingsEntity(
                    settingsJson = json.encodeToString(UserSettings.serializer(), document.settings),
                    legacyJson = document.legacy.takeIf { it.isNotEmpty() }?.toString(),
                ),
            )
        }
    }

    private suspend fun daysInFy(fyYear: Int): List<BackupDay> {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        return dayDao.getBetween(start.toString(), end.toString()).map { it.toBackupDay() }
    }

    private suspend fun save(day: BackupDay): DayEntry {
        if (DiaryRules.isEmpty(day)) dayDao.delete(day.date) else dayDao.upsert(day.toEntity(clock()))
        return DiaryRules.toDayEntry(day)
    }

    private fun requireInFy(fyYear: Int, date: String) {
        val (start, end) = FinancialYearHelper.fyRange(fyYear)
        val d = parseDate(date)
        require(d in start..end) { "Date $date is outside financial year $fyYear." }
    }

    private fun parseDate(value: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date: $value", e)
        }

    private fun DayEntryEntity.toBackupDay() =
        BackupDay(date, startTime, finishTime, breakHours, description, location)

    private fun BackupDay.toEntity(now: Long) =
        DayEntryEntity(date, startTime, finishTime, breakHours, description, location, now)

    private fun LeavePeriodEntity.toLeavePeriod() = LeavePeriod(id, startDate, endDate, note)
}
