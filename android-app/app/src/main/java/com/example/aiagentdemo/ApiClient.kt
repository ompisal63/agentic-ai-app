package com.example.aiagentdemo

import okhttp3.OkHttpClient
import okhttp3.Request

object ApiClient {

    private val client = OkHttpClient()

    fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    fun getClient(): OkHttpClient = client
}
