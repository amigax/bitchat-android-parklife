package com.bitchat.android.services

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FigletService {

    private val client = OkHttpClient()

    suspend fun generateFigletText(text: String): String? {
        return suspendCoroutine { continuation ->
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val request = Request.Builder()
                .url("https://durokotte.foo.ng/figlet-api/?text=$encodedText")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    continuation.resume(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (response.isSuccessful) {
                        continuation.resume(response.body?.string())
                    } else {
                        continuation.resume(null)
                    }
                }
            })
        }
    }
}
