package com.auroro.wallpapers.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryInterceptorTest {
    @Test fun quotaSensitiveHostGetsOnlyOneAttemptForServerFailures() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse.Builder().code(503).build())
            server.enqueue(MockResponse.Builder().code(200).body("ok").build())
            val host = server.url("/").host
            val client = OkHttpClient.Builder()
                .addInterceptor(RetryInterceptor(noRetryHosts = setOf(host)))
                .build()

            client.newCall(Request.Builder().url(server.url("/v1/images/")).build()).execute().use { response ->
                assertEquals(503, response.code)
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.close()
        }
    }
}
