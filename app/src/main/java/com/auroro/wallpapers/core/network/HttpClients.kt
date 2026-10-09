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
            .addInterceptor(RetryInterceptor(noRetryHosts = setOf("api.openverse.org")))
            .addNetworkInterceptor(HostAllowlistInterceptor())
            .build()
    }

    /** Coil requests use provider-validated original image URLs hosted by third-party HTTPS domains. */
    fun images(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .apply {
            networkInterceptors().removeAll { it is HostAllowlistInterceptor }
            addNetworkInterceptor(HostAllowlistInterceptor(allowPublicHttpsAssets = true))
        }
        .build()

    /** File downloads have no overall timeout, but retain a 30 s stall timeout for each read. */
    fun downloads(base: OkHttpClient, allowPublicHttpsAssets: Boolean = false): OkHttpClient = base.newBuilder()
        .apply {
            networkInterceptors().removeAll { it is HostAllowlistInterceptor }
            addNetworkInterceptor(HostAllowlistInterceptor(allowPublicHttpsAssets))
        }
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
