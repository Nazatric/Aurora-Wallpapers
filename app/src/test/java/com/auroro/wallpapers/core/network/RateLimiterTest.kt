package com.auroro.wallpapers.core.network

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    @Test fun localSlidingWindowWaitsForAnExpiredRequestSlot() = runTest {
        val limiter = SlidingWindowLimiter(
            maxEvents = 1,
            windowMs = 1_000,
            maxWaitMs = 1_000,
            now = { testScheduler.currentTime },
        )

        limiter.acquire("NASA APOD")
        val second = launch { limiter.acquire("NASA APOD") }
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(second.isCompleted)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test fun retryAfterCooldownWaitsBeforeTheNextRequest() = runTest {
        val limiter = SlidingWindowLimiter(
            maxEvents = 10,
            windowMs = 60_000,
            maxWaitMs = 2_000,
            now = { testScheduler.currentTime },
        )
        limiter.pauseFor(1_500)
        val request = launch { limiter.acquire("Wallhaven") }
        advanceTimeBy(1_500)
        runCurrent()

        assertTrue(request.isCompleted)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test fun commonProviderCallAppliesRetryAfterToFollowingRequests() = runTest {
        val limiter = SlidingWindowLimiter(
            maxEvents = 10,
            windowMs = 60_000,
            maxWaitMs = 3_000,
            now = { testScheduler.currentTime },
        )
        val responseError = ProviderException(ProviderErrorKind.RATE_LIMITED, "Slow down", retryAfterSeconds = 2)
        val failure = runCatching {
            RateLimitedCall.run(limiter, "Art Institute of Chicago") { throw responseError }
        }.exceptionOrNull()
        assertEquals(ProviderErrorKind.RATE_LIMITED, (failure as? ProviderException)?.kind)

        val request = launch { limiter.acquire("Art Institute of Chicago") }
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(request.isCompleted)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test fun excessiveWaitIsReportedWithRetryAfterInsteadOfSleepingIndefinitely() = runTest {
        val limiter = SlidingWindowLimiter(
            maxEvents = 1,
            windowMs = 60_000,
            maxWaitMs = 1_000,
            now = { testScheduler.currentTime },
        )
        limiter.acquire("Art Institute of Chicago")

        val error = runCatching { limiter.acquire("Art Institute of Chicago") }.exceptionOrNull() as? ProviderException

        assertEquals(ProviderErrorKind.RATE_LIMITED, error?.kind)
        assertEquals(60L, error?.retryAfterSeconds)
    }
}
