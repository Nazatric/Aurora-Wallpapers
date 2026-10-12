package com.auroro.wallpapers.core.network

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ProviderErrorTest {
    @Test fun retryAfterSecondsHeaderIsPreserved() {
        val mapped = http429("37").toProviderException("Wallhaven")

        assertEquals(ProviderErrorKind.RATE_LIMITED, mapped.kind)
        assertEquals(37L, mapped.retryAfterSeconds)
    }

    @Test fun retryAfterHttpDateHeaderBecomesABoundedWaitDuration() {
        val header = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(90))
        val mapped = http429(header).toProviderException("NASA APOD")

        assertEquals(ProviderErrorKind.RATE_LIMITED, mapped.kind)
        assertNotNull(mapped.retryAfterSeconds)
        assertTrue(mapped.retryAfterSeconds!! in 1L..90L)
    }

    private fun http429(retryAfter: String): HttpException {
        val raw = OkHttpResponse.Builder()
            .request(Request.Builder().url("https://provider.example/api").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", retryAfter)
            .body("rate limited".toResponseBody("text/plain".toMediaType()))
            .build()
        return HttpException(Response.error<Unit>(raw.body!!, raw))
    }
}
