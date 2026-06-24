package dev.wfhdiary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.remote.CreateLeaveRequest
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationActionReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WfhDiaryApplication
        val date =
            intent.getStringExtra(NotificationChannels.EXTRA_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val action = intent.action ?: return
        val pending = goAsync()

        scope.launch {
            runCatching {
                val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                val fyYear = FinancialYearHelper.currentFyYear(localDate)
                val settings = app.diaryRepository.getSettings()
                when (action) {
                    NotificationChannels.ACTION_HOME ->
                        app.diaryRepository.upsertDay(
                            fyYear,
                            date,
                            UpsertDayEntryRequest(
                                startTime = settings.defaultHomeStart,
                                finishTime = settings.defaultHomeFinish,
                                breakHours = settings.defaultHomeBreakHours,
                                location = "Home",
                            ),
                        )
                    NotificationChannels.ACTION_OFFICE ->
                        app.diaryRepository.upsertDay(
                            fyYear,
                            date,
                            UpsertDayEntryRequest(
                                startTime = settings.defaultOfficeStart,
                                finishTime = settings.defaultOfficeFinish,
                                breakHours = settings.defaultOfficeBreakHours,
                                location = "Office",
                            ),
                        )
                    NotificationChannels.ACTION_LEAVE ->
                        app.diaryRepository.createLeave(
                            fyYear,
                            CreateLeaveRequest(startDate = date, endDate = date),
                        )
                }
            }
            pending.finish()
        }
    }
}
