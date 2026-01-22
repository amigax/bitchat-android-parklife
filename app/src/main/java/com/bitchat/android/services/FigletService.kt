package com.bitchat.android.services

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.util.Log // Add this import for logging

object FigletService {

    private val client = OkHttpClient()
    private const val TAG = "FigletService" // Tag for logging

    suspend fun generateFigletText(text: String): String? {
        return suspendCoroutine { continuation ->
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val request = Request.Builder()
                .url("https://durokotte.foo.ng/figlet-api/?text=$encodedText")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    Log.e(TAG, "FIGlet API call failed", e) // Log the failure
                    continuation.resume(null)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "FIGlet API raw response: ${responseBody}") // Log raw response

                    if (response.isSuccessful && responseBody != null) {
                        // Simple check if it looks like HTML (case-insensitive)
                        if (responseBody.contains("<html>", ignoreCase = true) ||
                            responseBody.contains("<head>", ignoreCase = true) ||
                            responseBody.contains("<body>", ignoreCase = true)) {
                            Log.w(TAG, "FIGlet API returned HTML content, expected plain text. Returning null.")
                            continuation.resume(null) // Treat HTML as an invalid FIGlet response
                        } else {
                            // Assume it's plain ASCII art
                            continuation.resume(responseBody)
                        }
                    } else {
                        Log.e(TAG, "FIGlet API unsuccessful response: ${response.code} - ${response.message}. Returning null.")
                        continuation.resume(null)
                    }
                }
            })
        }
    }
}
