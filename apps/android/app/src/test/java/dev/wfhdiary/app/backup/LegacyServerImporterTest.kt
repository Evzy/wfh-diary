package dev.wfhdiary.app.backup

import dev.wfhdiary.app.data.remote.DiaryRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LegacyServerImporterTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneId.of("UTC"))

    private val settingsJson =
        """{"displayName":"Me","notificationTime":"08:00","defaultHomeStart":"08:00","defaultHomeFinish":"17:00",
           "defaultHomeBreakHours":1.0,"defaultOfficeStart":"08:00","defaultOfficeFinish":"17:00","defaultOfficeBreakHours":1.0}"""

    private fun daysJson(fy: Int) =
        when (fy) {
            2024 ->
                """[{"date":"2024-07-01","dayName":"Monday","startTime":"08:00","finishTime":"17:00","breakHours":1.0,"totalHours":8.0,"location":"Home"},
                       {"date":"2024-07-02","dayName":"Tuesday","totalHours":0.0}]"""
            2026 ->
                """[{"date":"2026-07-01","dayName":"Wednesday","description":"LEAVE","totalHours":0.0}]"""
            else -> "[]"
        }

    private fun importer(failFy: Int? = null): Pair<LegacyServerImporter, MutableList<String>> {
        val requested = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath
                requested += path
                val json = "application/json"
                val fy = Regex("""/v1/diaries/(\d+)/days""").find(path)?.groupValues?.get(1)?.toInt()
                when {
                    fy != null && fy == failFy -> respond("boom", HttpStatusCode.InternalServerError)
                    fy != null -> respond(daysJson(fy), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    path.endsWith("/v1/settings") -> respond(settingsJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, json))
                    path.endsWith("/v1/leave-periods") ->
                        respond(
                            """[{"id":"L1","startDate":"2026-07-01","endDate":"2026-07-01"}]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, json),
                        )
                    path.endsWith("/v1/work-templates") ->
                        respond(
                            """[{"id":"T1","label":"Home day"}]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, json),
                        )
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            }
        val client =
            HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        val repo = DiaryRepository(client) { "https://example.test/" }
        return LegacyServerImporter(repo, "test", clock) to requested
    }

    @Test
    fun imports_all_fys_skipping_empty_days() = runBlocking {
        val (importer, requested) = importer()
        val doc = importer.fetch()
        assertEquals("Me", doc.settings.displayName)
        assertEquals(listOf("2024-07-01", "2026-07-01"), doc.days.map { it.date })
        assertEquals("L1", doc.leavePeriods.single().id)
        assertEquals(1, doc.legacy!!["workTemplates"]!!.jsonArray.size)
        assertTrue("/v1/diaries/2020/days" in requested)
        assertTrue("/v1/diaries/2026/days" in requested)
        assertTrue(BackupCodec.validate(doc) is BackupParseResult.Success)
    }

    @Test(expected = Exception::class)
    fun any_failure_throws() {
        runBlocking { importer(failFy = 2023).first.fetch() }
    }
}
