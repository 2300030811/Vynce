/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Simplified AI orchestrator using Groq's free API.
 * Inspired by PixelMusic's AiOrchestrator but streamlined for a single provider.
 */

package com.vynce.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Experimental Feature
 *
 * AI Playlist is disabled by default and may be removed
 * in a future release depending on adoption and API costs.
 */
class GroqOrchestrator(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        private const val TAG = "GroqOrchestrator"
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        const val FAST_MODEL = "openai/gpt-oss-20b"
        private const val REQUEST_TIMEOUT_MS = 60_000L

        val AVAILABLE_MODELS = listOf(
            "openai/gpt-oss-120b" to "GPT-OSS 120B (Best quality)",
            "qwen/qwen3.6-27b" to "Qwen 3.6 27B (Good balance)",
            "openai/gpt-oss-20b" to "GPT-OSS 20B (Fastest)",
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Message(val role: String, val content: String)

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Float = 0.6f,
        val max_tokens: Int = 2048,
    )

    @Serializable
    data class ChatResponse(
        val choices: List<Choice> = emptyList(),
        val error: ErrorResponse? = null,
    )

    @Serializable
    data class Choice(
        val message: Message? = null,
    )

    @Serializable
    data class ErrorResponse(
        val message: String? = null,
        val type: String? = null,
    )

    /**
     * Generate content using Groq's chat completion API.
     */
    suspend fun generateContent(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.6f,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception(
                "No Groq API key configured. Get a free key at console.groq.com and add it in Settings → AI."
            ))
        }

        var lastException: Exception? = null
        var lastUserMessage = "AI request failed"
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                val requestBody = json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            Message("system", systemPrompt),
                            Message("user", userPrompt),
                        ),
                        temperature = temperature,
                    )
                )

                val request = Request.Builder()
                    .url(BASE_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withTimeout(REQUEST_TIMEOUT_MS) {
                    client.newCall(request).execute()
                }

                val body = response.body.string()

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        json.decodeFromString<ChatResponse>(body).error?.message ?: body
                    } catch (_: Exception) { body }

                    val userMessage = when (response.code) {
                        401 -> "Invalid API key. Check your Groq API key in Settings → AI."
                        429 -> "Rate limit exceeded. Groq's free tier has usage limits. Wait a moment and try again."
                        503, 502 -> "Groq servers are temporarily overloaded. Try again in a moment."
                        else -> "Groq API error (${response.code}): $errorMsg"
                    }
                    
                    Log.e(TAG, "Groq API error ${response.code}: $errorMsg (Attempt $attempt/$maxAttempts)")

                    // Retry for rate limit (429) or server errors (502, 503)
                    if ((response.code == 429 || response.code == 502 || response.code == 503) && attempt < maxAttempts) {
                        val backoffMs = attempt * 1000L
                        Log.d(TAG, "Retrying Groq API request in ${backoffMs}ms...")
                        delay(backoffMs)
                        continue
                    }

                    return@withContext Result.failure(Exception(userMessage))
                }

                val chatResponse = json.decodeFromString<ChatResponse>(body)
                val content = chatResponse.choices.firstOrNull()?.message?.content

                if (content.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("AI returned an empty response. Try rephrasing your prompt."))
                }

                Log.d(TAG, "Groq response received (${content.length} chars)")
                return@withContext Result.success(content)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Timeout on attempt $attempt", e)
                    lastException = e
                    lastUserMessage = "Request timed out. Groq may be overloaded — try again."
                } else {
                    throw e
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Network down", e)
                return@withContext Result.failure(Exception("No internet connection. Check your network and try again."))
            } catch (e: Exception) {
                Log.e(TAG, "Exception on attempt $attempt", e)
                lastException = e
                lastUserMessage = "AI request failed: ${e.message}"
            }

            if (attempt < maxAttempts) {
                val backoffMs = attempt * 1000L
                delay(backoffMs)
            }
        }

        Result.failure(lastException ?: Exception(lastUserMessage))
    }
}
