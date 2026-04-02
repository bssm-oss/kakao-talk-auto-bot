package com.example.kakaotalkautobot

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
