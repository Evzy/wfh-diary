package dev.wfhdiary.app.notification

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

class ApiErrorClassifierTest {
    @Test
    fun classify_connect_exception_as_server_waking() {
        assertEquals(
            DayLogFailureReason.SERVER_WAKING,
            ApiErrorClassifier.classify(ConnectException("Connection refused")),
        )
    }

    @Test
    fun classify_timeout_message_as_database_timeout() {
        assertEquals(
            DayLogFailureReason.DATABASE_TIMEOUT,
            ApiErrorClassifier.classify(IOException("Connection timed out")),
        )
    }

    @Test
    fun classify_connection_reset_as_database_timeout() {
        assertEquals(
            DayLogFailureReason.DATABASE_TIMEOUT,
            ApiErrorClassifier.classify(IOException("Connection reset by peer")),
        )
    }

    @Test
    fun classify_connect_timeout_exception_as_database_timeout() {
        assertEquals(
            DayLogFailureReason.DATABASE_TIMEOUT,
            ApiErrorClassifier.classify(ConnectTimeoutException("connect timeout")),
        )
    }

    @Test
    fun classify_nested_connect_timeout_as_database_timeout() {
        val wrapped = RuntimeException("request failed", ConnectTimeoutException("connect timeout"))
        assertEquals(DayLogFailureReason.DATABASE_TIMEOUT, ApiErrorClassifier.classify(wrapped))
    }

    @Test
    fun classify_unknown_as_generic() {
        assertEquals(
            DayLogFailureReason.GENERIC,
            ApiErrorClassifier.classify(IllegalStateException("boom")),
        )
    }

    @Test
    fun classifyResponseStatus_503_as_server_waking() {
        assertEquals(
            DayLogFailureReason.SERVER_WAKING,
            ApiErrorClassifier.classifyResponseStatus(HttpStatusCode.ServiceUnavailable),
        )
    }

    @Test
    fun classifyResponseStatus_502_as_server_waking() {
        assertEquals(
            DayLogFailureReason.SERVER_WAKING,
            ApiErrorClassifier.classifyResponseStatus(HttpStatusCode.BadGateway),
        )
    }

    @Test
    fun classifyResponseStatus_504_as_server_waking() {
        assertEquals(
            DayLogFailureReason.SERVER_WAKING,
            ApiErrorClassifier.classifyResponseStatus(HttpStatusCode.GatewayTimeout),
        )
    }

    @Test
    fun classifyResponseStatus_401_as_sign_in_required() {
        assertEquals(
            DayLogFailureReason.SIGN_IN_REQUIRED,
            ApiErrorClassifier.classifyResponseStatus(HttpStatusCode.Unauthorized),
        )
    }

    @Test
    fun classifyResponseStatus_500_as_generic() {
        assertEquals(
            DayLogFailureReason.GENERIC,
            ApiErrorClassifier.classifyResponseStatus(HttpStatusCode.InternalServerError),
        )
    }
}
