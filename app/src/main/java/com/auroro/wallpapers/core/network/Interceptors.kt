package com.auroro.wallpapers.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.random.Random

/** Blocks any hop (including redirects) that leaves the provider allow-list. Install as a *network* interceptor. */
class HostAllowlistInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!UrlPolicy.isAllowedForNetwork(url)) throw BlockedUrlException(url.host)
        return chain.proceed(chain.request())
    }
}

class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}

/**
 * Safe retries for idempotent GETs: connection failures, timeouts and 5xx/408 with exponential backoff
 * plus jitter. Never retries 401, 403 or 429 (those are returned as-is for the caller to explain).
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            try {
                val response = chain.proceed(request)
                if (response.code !in RETRYABLE_CODES || attempt >= maxRetries) return response
                response.close()
            } catch (e: BlockedUrlException) {
                throw e
            } catch (e: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled() || e is InterruptedIOException && e.message == "interrupted") throw e
            }
            attempt++
            val delay = baseDelayMs * (1L shl (attempt - 1)) + Random.nextLong(0, 100)
            try {
                sleeper(delay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("interrupted")
            }
        }
    }

    companion object {
        val RETRYABLE_CODES = setOf(408, 500, 502, 503, 504)
    }
}
