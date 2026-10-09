package com.auroro.wallpapers.core.network

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {
    const val USER_AGENT = "AuroroWallpapers/1.0 (Android; +https://github.com/Nazatric/Aurora-Wallpapers)"

    /**
     * Base client: HTTPS allow-list (also on redirects), retries with backoff, sane timeouts and a
     * bounded dispatcher so a fast scroll can't open dozens of sockets.
     */
    fun base(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 6
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false) // never follow an https -> http downgrade
            .addInterceptor(UserAgentInterceptor(USER_AGENT))
            .addInterceptor(RetryInterceptor())
            .addNetworkInterceptor(HostAllowlistInterceptor())
            .build()
    }

    /** For large file downloads: no overall call timeout, but a 30 s stall timeout per read. */
    fun downloads(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
