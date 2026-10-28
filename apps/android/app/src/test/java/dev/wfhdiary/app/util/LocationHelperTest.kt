package dev.wfhdiary.app.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationHelperTest {
    @Test
    fun currentOrFallback_uses_current_when_it_returns() = runTest {
        val result = LocationHelper.currentOrFallback(30_000, { "current" }, { "last" })
        assertEquals("current", result)
    }

    @Test
    fun currentOrFallback_uses_fallback_when_current_hangs() = runTest {
        val result = LocationHelper.currentOrFallback<String>(30_000, { awaitCancellation() }, { "last" })
        assertEquals("last", result)
        assertEquals(30_000L, currentTime)
    }

    @Test
    fun currentOrFallback_uses_fallback_when_current_null_or_throws() = runTest {
        assertEquals("last", LocationHelper.currentOrFallback(30_000, { null }, { "last" }))
        assertEquals(
            "last",
            LocationHelper.currentOrFallback<String>(30_000, { error("boom") }, { "last" }),
        )
    }

    @Test
    fun currentOrFallback_null_when_nothing_available() = runTest {
        assertNull(LocationHelper.currentOrFallback<String>(30_000, { null }, { null }))
    }
}
