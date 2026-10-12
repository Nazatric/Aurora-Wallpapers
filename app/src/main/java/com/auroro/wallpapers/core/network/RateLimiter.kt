package com.auroro.wallpapers.core.network

import android.content.SharedPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent sliding-window limiter. The optional preference store keeps request pacing and
 * Retry-After cooldowns bounded across process restarts.
 */
class SlidingWindowLimiter(
    private val maxEvents: Int,
    private val windowMs: Long,
    private val maxWaitMs: Long = 8_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val preferences: SharedPreferences? = null,
    private val storageKey: String? = null,
) {
    private data class RemoteBudget(val remaining: Int, val resetAt: Long)

    private val mutex = Mutex()
    private val events = ArrayDeque<Long>().apply {
        val stored = if (preferences != null && storageKey != null) preferences.getString(storageKey, null) else null
        stored?.split(',')?.mapNotNull(String::toLongOrNull)?.forEach(::addLast)
    }
    private val remoteBudgets = LinkedHashMap<String, RemoteBudget>().apply {
        val stored = if (preferences != null && storageKey != null) preferences.getString("${storageKey}_provider", null) else null
        stored?.split(';')?.forEach { record ->
            val fields = record.split(':')
            if (fields.size == 3 && fields[0] in PERSISTED_BUDGETS) {
                val remaining = fields[1].toIntOrNull()
                val resetAt = fields[2].toLongOrNull()
                if (remaining != null && resetAt != null) put(fields[0], RemoteBudget(remaining, resetAt))
            }
        }
    }

    init {
        require(maxEvents > 0)
        require(windowMs > 0)
        require(maxWaitMs >= 0)
        require((preferences == null) == (storageKey == null))
    }

    suspend fun acquire(providerName: String) {
        mutex.withLock {
            while (true) {
                val time = now()
                prune(time)
                val exhausted = remoteBudgets.values.filter { it.remaining <= 0 }
                if (exhausted.isNotEmpty()) {
                    val wait = (exhausted.minOf { it.resetAt } - time).coerceAtLeast(1)
                    if (wait > maxWaitMs) throw limited(providerName, wait)
                    delay(wait)
                    continue
                }

                if (events.size < maxEvents) {
                    events.addLast(time)
                    remoteBudgets.replaceAll { _, budget -> budget.copy(remaining = budget.remaining - 1) }
                    persist()
                    return
                }

                val wait = (windowMs - (time - events.first()).coerceAtLeast(0)).coerceAtLeast(1)
                if (wait > maxWaitMs) throw limited(providerName, wait)
                delay(wait)
            }
        }
    }

    /** Use a provider's Retry-After on a 429 even if a proxy omits quota headers. */
    suspend fun pauseFor(milliseconds: Long) {
        if (milliseconds <= 0) return
        mutex.withLock {
            remoteBudgets["retry_after"] = RemoteBudget(0, now() + milliseconds)
            persist()
        }
    }

    private fun prune(time: Long) {
        var localChanged = false
        while (events.isNotEmpty() && time - events.first() >= windowMs) {
            events.removeFirst()
            localChanged = true
        }
        val remoteChanged = remoteBudgets.entries.removeAll { it.value.resetAt <= time }
        if (localChanged || remoteChanged) persist()
    }

    private fun persist() {
        val prefs = preferences ?: return
        val key = storageKey ?: return
        prefs.edit()
            .putString(key, events.joinToString(","))
            .putString("${key}_provider", remoteBudgets.entries.joinToString(";") { (bucket, budget) ->
                "$bucket:${budget.remaining}:${budget.resetAt}"
            })
            .apply()
    }

    private fun limited(providerName: String, milliseconds: Long) = ProviderException(
        ProviderErrorKind.RATE_LIMITED,
        "Slowing down to respect $providerName's request limit. Try again in ${(milliseconds + 999) / 1000}s.",
        retryAfterSeconds = (milliseconds + 999) / 1000,
    )

    private companion object {
        val PERSISTED_BUDGETS = setOf("retry_after")
    }
}

/** Runs a Retrofit call behind a limiter and maps every failure to a [ProviderException]. */
object RateLimitedCall {
    suspend fun <T> run(limiter: SlidingWindowLimiter, providerName: String, block: suspend () -> T): T {
        limiter.acquire(providerName)
        return try {
            block()
        } catch (t: Throwable) {
            val error = t.toProviderException(providerName)
            if (error.kind == ProviderErrorKind.RATE_LIMITED) {
                error.retryAfterSeconds?.takeIf { it > 0 }?.let { seconds ->
                    limiter.pauseFor(seconds.coerceAtMost(MAX_RETRY_AFTER_SECONDS) * 1_000L)
                }
            }
            throw error
        }
    }

    private const val MAX_RETRY_AFTER_SECONDS = 604_800L
}
