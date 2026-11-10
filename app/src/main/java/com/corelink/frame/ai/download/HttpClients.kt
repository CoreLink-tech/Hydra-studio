package com.corelink.frame.ai.download

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared client for model downloads. Long read timeout because multi-GB files stream slowly. */
object HttpClients {
    fun default(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
