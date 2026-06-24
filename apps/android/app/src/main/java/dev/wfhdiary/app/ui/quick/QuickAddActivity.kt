package dev.wfhdiary.app.ui.quick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wfhdiary.app.R
import dev.wfhdiary.app.WfhDiaryApplication
import dev.wfhdiary.app.data.remote.UpsertDayEntryRequest
import dev.wfhdiary.app.notification.NotificationChannels
import dev.wfhdiary.app.ui.theme.WfhDiaryTheme
import dev.wfhdiary.app.util.FinancialYearHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date =
            intent.getStringExtra(NotificationChannels.EXTRA_DATE)
                ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        setContent {
            WfhDiaryTheme {
                val app = application as WfhDiaryApplication
                val scope = rememberCoroutineScope()
                var startTime by remember { mutableStateOf("") }
                var finishTime by remember { mutableStateOf("") }
                var location by remember { mutableStateOf("Home") }
                var error by remember { mutableStateOf<String?>(null) }

                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.quick_add_title) + " — $date")
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text(stringResource(R.string.day_edit_location)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text(stringResource(R.string.day_edit_start)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = finishTime,
                            onValueChange = { finishTime = it },
                            label = { Text(stringResource(R.string.day_edit_finish)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                                        val fyYear = FinancialYearHelper.currentFyYear(localDate)
                                        app.diaryRepository.upsertDay(
                                            fyYear,
                                            date,
                                            UpsertDayEntryRequest(
                                                startTime = startTime.ifBlank { null },
                                                finishTime = finishTime.ifBlank { null },
                                                location = location.ifBlank { null },
                                            ),
                                        )
                                        finish()
                                    }.onFailure { e ->
                                        error = e.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.quick_add_save))
                        }
                    }
                }
            }
        }
    }
}
