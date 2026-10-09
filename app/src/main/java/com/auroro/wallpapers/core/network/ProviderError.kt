package com.auroro.wallpapers.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ProviderErrorKind(val title: String) {
    NO_NETWORK("No connection"),
    TIMEOUT("Timed out"),
    RATE_LIMITED("Rate limited"),
    AUTH_FAILED("Authentication failed"),
    SERVER_ERROR("Server error"),
    BAD_RESPONSE("Unexpected response"),
    NOT_CONFIGURED("Not configured"),
    BLOCKED_URL("Blocked URL"),
    UNKNOWN("Something went wrong"),
}

/** The single error type providers expose. Always carries something the UI can explain to the user. */
class ProviderException(
    val kind: ProviderErrorKind,
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** 401/403/429 are never retried automatically. */
    val isAutoRetryable: Boolean
        get() = kind == ProviderErrorKind.NO_NETWORK || kind == ProviderErrorKind.TIMEOUT || kind == ProviderErrorKind.SERVER_ERROR
}

/** Thrown by [HostAllowlistInterceptor] when a request would leave the provider allowlist. */
class BlockedUrlException(url: String) : IOException("Blocked request to a non-allowlisted destination: $url")

/** Converts any failure from a provider call into a [ProviderException]. Cancellation is re-thrown untouched. */
fun Throwable.toProviderException(providerName: String): ProviderException {
    if (this is CancellationException) throw this
    return when (this) {
        is ProviderException -> this
        is HttpException -> {
            val retryAfter = response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()
            when (val c = code()) {
                401, 403 -> ProviderException(
                    ProviderErrorKind.AUTH_FAILED,
                    "$providerName denied this public request (HTTP $c). Check the source page or try again later.",
                    cause = this,
                )
                429 -> ProviderException(
                    ProviderErrorKind.RATE_LIMITED,
                    "$providerName rate limit reached." + (retryAfter?.takeIf { it > 0 }?.let { " Retry after $it seconds." } ?: " Try again later."),
                    retryAfter,
                    this,
                )
                in 500..599 -> ProviderException(ProviderErrorKind.SERVER_ERROR, "$providerName is having problems (HTTP $c).", cause = this)
                else -> ProviderException(ProviderErrorKind.BAD_RESPONSE, "$providerName returned HTTP $c.", cause = this)
            }
        }
        is BlockedUrlException -> ProviderException(ProviderErrorKind.BLOCKED_URL, message ?: "Blocked URL", cause = this)
        is SocketTimeoutException -> ProviderException(ProviderErrorKind.TIMEOUT, "$providerName took too long to respond.", cause = this)
        is InterruptedIOException ->
            ProviderException(ProviderErrorKind.TIMEOUT, "$providerName took too long to respond.", cause = this)
        is UnknownHostException, is ConnectException ->
            ProviderException(ProviderErrorKind.NO_NETWORK, "Can't reach $providerName. Check your internet connection.", cause = this)
        is SerializationException, is IllegalArgumentException ->
            ProviderException(ProviderErrorKind.BAD_RESPONSE, "$providerName sent a response Auroro couldn't read.", cause = this)
        is IOException -> ProviderException(ProviderErrorKind.NO_NETWORK, "Network problem while contacting $providerName.", cause = this)
        else -> ProviderException(ProviderErrorKind.UNKNOWN, message ?: "Unexpected error talking to $providerName.", cause = this)
    }
}
