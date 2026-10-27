package dev.wfhdiary.app.data.store

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.model.DayEntryDto
import dev.wfhdiary.app.data.model.DiarySummaryDto
import dev.wfhdiary.app.data.model.UpdateUserSettingsRequest
import dev.wfhdiary.app.data.model.UpsertDayEntryRequest
import dev.wfhdiary.app.data.model.UserSettingsDto
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.TimeHelpers
import dev.wfhdiary.app.util.WorkingDaysMask
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object DiaryRules {
    const val LEAVE = "LEAVE"
    private val WFH_RATE = BigDecimal("0.67")
    private val MISSED_DEFAULTS = setOf("Home", "Office", "Leave", "Geolocation", "None")
    private val AWAY_DEFAULTS = setOf("Home", "Office", "Leave")

    fun normalizeTime(value: String?): String? =
        TimeHelpers.parseTimeString(value)?.let(TimeHelpers::minutesToTimeString)

    fun applyUpsert(existing: BackupDay?, date: String, request: UpsertDayEntryRequest): BackupDay {
        var day = existing ?: BackupDay(date)
        if (request.startTime != null) day = day.copy(startTime = normalizeTime(request.startTime))
        if (request.finishTime != null) day = day.copy(finishTime = normalizeTime(request.finishTime))
        if (request.breakHours != null) day = day.copy(breakHours = request.breakHours)
        if (request.description != null) day = day.copy(description = request.description)
        if (request.location != null) day = day.copy(location = request.location.takeIf { it.isNotBlank() })
        return day
    }

    fun replace(date: String, request: UpsertDayEntryRequest): BackupDay =
        BackupDay(
            date = date,
            startTime = normalizeTime(request.startTime),
            finishTime = normalizeTime(request.finishTime),
            breakHours = request.breakHours,
            description = request.description?.takeIf { it.isNotBlank() },
            location = request.location?.takeIf { it.isNotBlank() },
        )

    fun isEmpty(day: BackupDay): Boolean =
        day.startTime == null &&
            day.finishTime == null &&
            day.breakHours == null &&
            day.description.isNullOrBlank() &&
            day.location.isNullOrBlank()

    fun totalHours(day: BackupDay): BigDecimal =
        TimeHelpers.computeTotalHours(
            TimeHelpers.parseTimeString(day.startTime),
            TimeHelpers.parseTimeString(day.finishTime),
            day.breakHours?.let { BigDecimal.valueOf(it) },
        )

    fun dayName(date: String): String =
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun toDto(day: BackupDay): DayEntryDto =
        DayEntryDto(
            date = day.date,
            dayName = dayName(day.date),
            startTime = day.startTime,
            finishTime = day.finishTime,
            breakHours = day.breakHours,
            totalHours = totalHours(day).toDouble(),
            description = day.description,
            location = day.location,
        )

    fun leaveDatesInFy(fyYear: Int, start: LocalDate, end: LocalDate): List<LocalDate> {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val from = maxOf(start, fyStart)
        val to = minOf(end, fyEnd)
        if (from > to) return emptyList()
        return FinancialYearHelper.eachDay(from, to).toList()
    }

    fun leaveDay(date: String): BackupDay = BackupDay(date = date, description = LEAVE)

    fun datesToClearOnLeaveDelete(
        removed: ClosedRange<LocalDate>,
        others: List<ClosedRange<LocalDate>>,
    ): List<LocalDate> =
        FinancialYearHelper.eachDay(removed.start, removed.endInclusive)
            .filter { date -> others.none { date in it } }
            .toList()

    fun summarize(days: Collection<BackupDay>): DiarySummaryDto {
        var total = BigDecimal.ZERO
        var home = BigDecimal.ZERO
        var office = BigDecimal.ZERO
        var daysAtHome = 0
        var daysInOffice = 0
        for (day in days) {
            val hours = totalHours(day)
            total += hours
            when {
                day.location.equals("Home", ignoreCase = true) -> {
                    home += hours
                    if (hours.signum() > 0) daysAtHome++
                }
                day.location.equals("Office", ignoreCase = true) -> {
                    office += hours
                    if (hours.signum() > 0) daysInOffice++
                }
            }
        }
        val claimable = home.multiply(WFH_RATE).setScale(2, RoundingMode.HALF_EVEN)
        return DiarySummaryDto(
            totalHours = total.toDouble(),
            homeHours = home.toDouble(),
            officeHours = office.toDouble(),
            claimableWfhAmount = claimable.toDouble(),
            daysAtHome = daysAtHome,
            daysInOffice = daysInOffice,
            logicCheckPassed = total.compareTo(home + office) == 0,
        )
    }

    fun defaultSettings(): UserSettingsDto =
        UserSettingsDto(
            displayName = "YOUR NAME HERE",
            notificationTime = "08:00",
            notificationWorkingDaysMask = WorkingDaysMask.DEFAULT,
            missedNotificationDefault = "None",
            missedNotificationApplyAt = "12:00",
            missedNotificationAwayDefault = "Office",
            defaultHomeStart = "08:00",
            defaultHomeFinish = "17:00",
            defaultHomeBreakHours = 1.0,
            defaultOfficeStart = "08:00",
            defaultOfficeFinish = "17:00",
            defaultOfficeBreakHours = 1.0,
        )

    fun applySettingsUpdate(current: UserSettingsDto, r: UpdateUserSettingsRequest): UserSettingsDto {
        var s = current
        r.displayName?.let { s = s.copy(displayName = it) }
        normalizeTime(r.notificationTime)?.let { s = s.copy(notificationTime = it) }
        normalizeTime(r.defaultHomeStart)?.let { s = s.copy(defaultHomeStart = it) }
        normalizeTime(r.defaultHomeFinish)?.let { s = s.copy(defaultHomeFinish = it) }
        r.defaultHomeBreakHours?.let { s = s.copy(defaultHomeBreakHours = it) }
        r.defaultHomeDescription?.let { s = s.copy(defaultHomeDescription = it.trim().ifEmpty { null }) }
        normalizeTime(r.defaultOfficeStart)?.let { s = s.copy(defaultOfficeStart = it) }
        normalizeTime(r.defaultOfficeFinish)?.let { s = s.copy(defaultOfficeFinish = it) }
        r.defaultOfficeBreakHours?.let { s = s.copy(defaultOfficeBreakHours = it) }
        r.defaultOfficeDescription?.let { s = s.copy(defaultOfficeDescription = it.trim().ifEmpty { null }) }
        r.notificationWorkingDaysMask?.let { s = s.copy(notificationWorkingDaysMask = it) }
        r.missedNotificationDefault?.let {
            val d = it.trim()
            s = s.copy(missedNotificationDefault = if (d in MISSED_DEFAULTS) d else "None")
        }
        r.missedNotificationAwayDefault?.let {
            val away = it.trim()
            s = s.copy(missedNotificationAwayDefault = if (away in AWAY_DEFAULTS) away else "Office")
        }
        r.geolocationSkipNotificationAtHome?.let { s = s.copy(geolocationSkipNotificationAtHome = it) }
        r.geolocationSkipNotificationAtOffice?.let { s = s.copy(geolocationSkipNotificationAtOffice = it) }
        normalizeTime(r.missedNotificationApplyAt)?.let { s = s.copy(missedNotificationApplyAt = it) }

        if (r.clearHomeGeofence == true) {
            s = s.copy(homeGeofenceLatitude = null, homeGeofenceLongitude = null, homeGeofenceRadiusMeters = null)
        } else if (r.homeGeofenceLatitude != null && r.homeGeofenceLongitude != null && r.homeGeofenceRadiusMeters != null) {
            s = s.copy(
                homeGeofenceLatitude = r.homeGeofenceLatitude,
                homeGeofenceLongitude = r.homeGeofenceLongitude,
                homeGeofenceRadiusMeters = r.homeGeofenceRadiusMeters,
            )
        }
        if (r.clearOfficeGeofence == true) {
            s = s.copy(officeGeofenceLatitude = null, officeGeofenceLongitude = null, officeGeofenceRadiusMeters = null)
        } else if (r.officeGeofenceLatitude != null && r.officeGeofenceLongitude != null && r.officeGeofenceRadiusMeters != null) {
            s = s.copy(
                officeGeofenceLatitude = r.officeGeofenceLatitude,
                officeGeofenceLongitude = r.officeGeofenceLongitude,
                officeGeofenceRadiusMeters = r.officeGeofenceRadiusMeters,
            )
        }
        return s
    }
}
