package dev.wfhdiary.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class ContractDeserializationTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun userSettings_fixture_deserializes() {
        assertNotNull(json.decodeFromString<UserSettingsDto>(readContract("user-settings.json")))
    }

    @Test
    fun dayEntry_fixture_deserializes() {
        assertNotNull(json.decodeFromString<DayEntryDto>(readContract("day-entry.json")))
    }

    @Test
    fun leavePeriod_fixture_deserializes() {
        assertNotNull(json.decodeFromString<LeavePeriodDto>(readContract("leave-period.json")))
    }

    @Test
    fun diarySummary_fixture_deserializes() {
        assertNotNull(json.decodeFromString<DiarySummaryDto>(readContract("diary-summary.json")))
    }

    @Test
    fun diary_fixture_deserializes() {
        assertNotNull(json.decodeFromString<DiaryDto>(readContract("diary.json")))
    }

    private fun readContract(name: String): String {
        val start = File(checkNotNull(System.getProperty("user.dir")))
        val root =
            generateSequence(start) { it.parentFile }
                .first { File(it, "version.json").exists() }
        return File(root, "packages/shared/contracts/$name").readText()
    }
}
