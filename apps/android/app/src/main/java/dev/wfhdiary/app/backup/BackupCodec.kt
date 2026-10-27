package dev.wfhdiary.app.backup

import dev.wfhdiary.app.util.TimeHelpers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

object BackupCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }

    fun encode(document: BackupDocument): String =
        json.encodeToString(BackupDocument.serializer(), document)

    fun decode(text: String): BackupParseResult {
        val root =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return BackupParseResult.Failure("Not a valid backup file (unreadable JSON).")
            }
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != BackupDocument.FORMAT) {
            return BackupParseResult.Failure("Not a WFH Diary backup file.")
        }
        val version =
            root["version"]?.jsonPrimitive?.intOrNull
                ?: return BackupParseResult.Failure("Backup file has no version.")
        if (version > BackupDocument.CURRENT_VERSION) {
            return BackupParseResult.Failure(
                "Backup was made by a newer app (format version $version). Update the app first.",
            )
        }
        val document =
            try {
                json.decodeFromJsonElement(BackupDocument.serializer(), root)
            } catch (e: Exception) {
                return BackupParseResult.Failure("Backup file is damaged: ${e.message}")
            }
        return validate(document)
    }

    fun validate(document: BackupDocument): BackupParseResult {
        val s = document.settings
        val settingsTimes =
            listOf(
                s.notificationTime,
                s.missedNotificationApplyAt,
                s.defaultHomeStart,
                s.defaultHomeFinish,
                s.defaultOfficeStart,
                s.defaultOfficeFinish,
            )
        if (settingsTimes.any { TimeHelpers.parseTimeString(it) == null }) {
            return BackupParseResult.Failure("Backup settings contain an invalid time.")
        }

        val seen = HashSet<String>()
        for (day in document.days) {
            if (parseDate(day.date) == null) {
                return BackupParseResult.Failure("Invalid day date: ${day.date}")
            }
            if (!seen.add(day.date)) {
                return BackupParseResult.Failure("Day appears twice: ${day.date}")
            }
            if (!isValidOptionalTime(day.startTime) || !isValidOptionalTime(day.finishTime)) {
                return BackupParseResult.Failure("Invalid time on ${day.date}")
            }
            val breakHours = day.breakHours
            if (breakHours != null && (breakHours < 0 || breakHours.isNaN())) {
                return BackupParseResult.Failure("Invalid break hours on ${day.date}")
            }
        }

        val leave = ArrayList<BackupLeavePeriod>(document.leavePeriods.size)
        for (period in document.leavePeriods) {
            if (period.id.isBlank()) {
                return BackupParseResult.Failure("Leave period without an id.")
            }
            val start = parseDate(period.startDate)
            val end = parseDate(period.endDate)
            if (start == null || end == null) {
                return BackupParseResult.Failure("Invalid leave dates: ${period.startDate}..${period.endDate}")
            }
            leave +=
                if (end < start) {
                    period.copy(startDate = end.toString(), endDate = start.toString())
                } else {
                    period
                }
        }
        return BackupParseResult.Success(document.copy(leavePeriods = leave))
    }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun isValidOptionalTime(value: String?): Boolean =
        value == null || TimeHelpers.parseTimeString(value) != null
}
