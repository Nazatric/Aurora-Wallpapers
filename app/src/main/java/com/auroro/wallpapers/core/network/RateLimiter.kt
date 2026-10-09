package com.auroro.wallpapers.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sliding-window limiter used to stay under a provider's published quota
 * (Wallhaven: 45 requests/minute). If the wait would be unreasonably long, it fails fast with
 * a RATE_LIMITED error instead of leaving the UI spinning.
 */
class SlidingWindowLimiter(
    private val maxEvents: Int,
    private val windowMs: Long,
    private val maxWaitMs: Long = 8_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val events = ArrayDeque<Long>()

    suspend fun acquire(providerName: String) {
        mutex.withLock {
            while (true) {
                val t = now()
                while (events.isNotEmpty() && t - events.first() >= windowMs) events.removeFirst()
                if (events.size < maxEvents) {
                    events.addLast(t)
                    return
                }
                val wait = windowMs - (t - events.first())
                if (wait > maxWaitMs) {
                    throw ProviderException(
                        ProviderErrorKind.RATE_LIMITED,
                        "Slowing down to respect $providerName's request limit. Try again in ${(wait + 999) / 1000}s.",
                        retryAfterSeconds = (wait + 999) / 1000,
                    )
                }
                delay(wait)
            }
        }
    }
}

/** Runs a Retrofit call behind a limiter and maps every failure to a [ProviderException]. */
object RateLimitedCall {
    suspend fun <T> run(limiter: SlidingWindowLimiter, providerName: String, block: suspend () -> T): T {
        limiter.acquire(providerName)
        return try {
            block()
        } catch (t: Throwable) {
            throw t.toProviderException(providerName)
        }
    }
}
