package dev.wfhdiary.app.notification

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.ConnectException

enum class DayLogFailureReason {
    SERVER_WAKING,
    DATABASE_TIMEOUT,
    SIGN_IN_REQUIRED,
    GENERIC,
}

object ApiErrorClassifier {
    fun classify(error: Throwable): DayLogFailureReason {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is HttpRequestTimeoutException,
                is ConnectTimeoutException,
                is SocketTimeoutException,
                -> return DayLogFailureReason.DATABASE_TIMEOUT
                is ConnectException,
                is IOException,
                -> {
                    if (messageIndicatesTimeout(current.message)) {
                        return DayLogFailureReason.DATABASE_TIMEOUT
                    }
                    return DayLogFailureReason.SERVER_WAKING
                }
                is ResponseException -> {
                    return classifyResponseStatus(current.response.status)
                }
            }
            current = current.cause
        }
        return DayLogFailureReason.GENERIC
    }

    private fun messageIndicatesTimeout(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("connection reset")
    }

    internal fun classifyResponseStatus(status: HttpStatusCode): DayLogFailureReason =
        when (status) {
            HttpStatusCode.Unauthorized -> DayLogFailureReason.SIGN_IN_REQUIRED
            HttpStatusCode.ServiceUnavailable,
            HttpStatusCode.BadGateway,
            HttpStatusCode.GatewayTimeout,
            -> DayLogFailureReason.SERVER_WAKING
            else -> DayLogFailureReason.GENERIC
        }
}
