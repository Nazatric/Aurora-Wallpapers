package com.auroro.wallpapers.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test fun imageSearchHonoursProviderSearchBudgets() = runBlocking {
        val limiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000, maxWaitMs = 1_000)
        limiter.observeProviderHeaders(
            Headers.Builder()
                .add("X-RateLimit-Limit-anon_sustained", "200/day")
                .add("X-RateLimit-Available-anon_sustained", "0")
                .build(),
        )

        val error = runCatching { limiter.acquire("Openverse") }.exceptionOrNull() as? ProviderException
        assertTrue(error != null)
        assertEquals(ProviderErrorKind.RATE_LIMITED, error?.kind)
    }

    @Test fun imageSearchDoesNotTreatThumbnailQuotaAsSearchQuota() = runBlocking {
        val limiter = SlidingWindowLimiter(maxEvents = 1, windowMs = 60_000, maxWaitMs = 1_000)
        limiter.observeProviderHeaders(
            Headers.Builder()
                .add("X-RateLimit-Limit-anon_thumbnail", "20/minute")
                .add("X-RateLimit-Available-anon_thumbnail", "0")
                .build(),
        )

        limiter.acquire("Openverse")
    }
}
