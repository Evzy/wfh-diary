package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.UserSettingsDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun settings() =
        UserSettingsDto(
            displayName = "Test User",
            notificationTime = "08:00",
            defaultHomeStart = "08:00",
            defaultHomeFinish = "17:00",
            defaultHomeBreakHours = 1.0,
            defaultOfficeStart = "08:30",
            defaultOfficeFinish = "17:00",
            defaultOfficeBreakHours = 0.5,
        )

    private fun document(
        days: List<BackupDay> = listOf(BackupDay("2025-07-01", "08:00", "17:00", 1.0, null, "Home")),
        leave: List<BackupLeavePeriod> = listOf(BackupLeavePeriod("a", "2025-12-22", "2026-01-02", "Xmas")),
        legacy: JsonObject = JsonObject(emptyMap()),
    ) = BackupDocument(
        exportedAt = "2026-10-27T09:00:00+11:00",
        appVersion = "1.0.0",
        settings = settings(),
        days = days,
        leavePeriods = leave,
        legacy = legacy,
    )

    private fun decodeOk(text: String): BackupDocument {
        val result = BackupCodec.decode(text)
        assertTrue("expected success but was $result", result is BackupParseResult.Success)
        return (result as BackupParseResult.Success).document
    }

    private fun decodeFails(text: String): String {
        val result = BackupCodec.decode(text)
        assertTrue("expected failure but was $result", result is BackupParseResult.Failure)
        return (result as BackupParseResult.Failure).message
    }

    @Test
    fun round_trip_preserves_document() {
        val doc = document()
        assertEquals(doc, decodeOk(BackupCodec.encode(doc)))
    }

    @Test
    fun encode_includes_format_and_version() {
        val text = BackupCodec.encode(document())
        assertTrue(text.contains("\"format\": \"wfh-diary-backup\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun encode_always_includes_legacy() {
        val text = BackupCodec.encode(document())
        assertTrue(text, text.contains("\"legacy\": {"))
    }

    @Test
    fun missing_legacy_decodes_as_empty() {
        val text = BackupCodec.encode(document()).replace(Regex(""",\s*"legacy": \{\s*}"""), "")
        assertTrue(!text.contains("legacy"))
        assertEquals(JsonObject(emptyMap()), decodeOk(text).legacy)
    }

    @Test
    fun legacy_section_is_preserved() {
        val legacy = JsonObject(mapOf("workTemplates" to JsonArray(listOf(JsonPrimitive("t1")))))
        val decoded = decodeOk(BackupCodec.encode(document(legacy = legacy)))
        assertEquals(legacy, decoded.legacy)
    }

    @Test
    fun unknown_fields_are_ignored() {
        val text = BackupCodec.encode(document()).replaceFirst("{", "{\"futureField\": 42,")
        decodeOk(text)
    }

    @Test
    fun malformed_json_is_refused() {
        decodeFails("{ not json")
    }

    @Test
    fun wrong_format_is_refused() {
        val text = BackupCodec.encode(document()).replace("wfh-diary-backup", "something-else")
        assertTrue(decodeFails(text).contains("Not a WFH Diary backup"))
    }

    @Test
    fun newer_version_is_refused() {
        val text = BackupCodec.encode(document()).replace("\"version\": 1", "\"version\": 2")
        assertTrue(decodeFails(text).contains("newer"))
    }

    @Test
    fun object_format_is_refused() {
        val text =
            BackupCodec.encode(document()).replace(
                "\"format\": \"wfh-diary-backup\"",
                "\"format\": {}",
            )
        assertTrue(decodeFails(text).contains("Not a WFH Diary backup"))
    }

    @Test
    fun array_version_is_refused() {
        val text = BackupCodec.encode(document()).replace("\"version\": 1", "\"version\": []")
        assertTrue(decodeFails(text).contains("no version"))
    }

    @Test
    fun zero_version_is_refused() {
        val text = BackupCodec.encode(document()).replace("\"version\": 1", "\"version\": 0")
        assertTrue(decodeFails(text).contains("Unsupported backup version 0"))
    }

    @Test
    fun invalid_day_date_is_refused() {
        val msg = decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-13-01")))))
        assertTrue(msg.contains("2025-13-01"))
    }

    @Test
    fun duplicate_day_is_refused() {
        val msg =
            decodeFails(
                BackupCodec.encode(
                    document(days = listOf(BackupDay("2025-07-01"), BackupDay("2025-07-01"))),
                ),
            )
        assertTrue(msg.contains("2025-07-01"))
    }

    @Test
    fun invalid_time_is_refused() {
        val msg =
            decodeFails(
                BackupCodec.encode(document(days = listOf(BackupDay("2025-07-01", startTime = "25:99")))),
            )
        assertTrue(msg.contains("2025-07-01"))
    }

    @Test
    fun negative_break_is_refused() {
        val msg =
            decodeFails(BackupCodec.encode(document(days = listOf(BackupDay("2025-07-01", breakHours = -1.0)))))
        assertTrue(msg.contains("2025-07-01"))
    }

    @Test
    fun invalid_settings_time_is_refused() {
        val doc = document().copy(settings = settings().copy(notificationTime = "nope"))
        assertTrue(decodeFails(BackupCodec.encode(doc)).contains("invalid time"))
    }

    @Test
    fun negative_settings_break_is_refused() {
        val doc = document().copy(settings = settings().copy(defaultOfficeBreakHours = -0.5))
        assertTrue(decodeFails(BackupCodec.encode(doc)).contains("invalid break hours"))
    }

    @Test
    fun duplicate_leave_id_is_refused() {
        val leave =
            listOf(
                BackupLeavePeriod("same-id", "2025-12-22", "2025-12-23"),
                BackupLeavePeriod("same-id", "2025-12-24", "2025-12-25"),
            )
        assertTrue(decodeFails(BackupCodec.encode(document(leave = leave))).contains("same-id"))
    }

    @Test
    fun reversed_leave_period_is_swapped() {
        val doc = document(leave = listOf(BackupLeavePeriod("a", "2026-01-02", "2025-12-22")))
        val decoded = decodeOk(BackupCodec.encode(doc))
        assertEquals("2025-12-22", decoded.leavePeriods.single().startDate)
        assertEquals("2026-01-02", decoded.leavePeriods.single().endDate)
    }

    @Test
    fun blank_leave_id_is_refused() {
        decodeFails(BackupCodec.encode(document(leave = listOf(BackupLeavePeriod(" ", "2025-12-22", "2025-12-23")))))
    }
}
