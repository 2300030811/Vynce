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
 * Groq AI client for playlist generation and other AI features.
 *
 * Groq offers a generous free tier with fast inference:
 * - llama-3.3-70b-versatile: Good for playlist curation
 * - llama-3.1-8b-instant: Faster, lighter option
 *
 * @param apiKey Groq API key from https://console.groq.com
 */
class GroqOrchestrator(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        private const val TAG = "GroqOrchestrator"
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        const val FAST_MODEL = "llama-3.1-8b-instant"
        private const val REQUEST_TIMEOUT_MS = 60_000L

        val AVAILABLE_MODELS = listOf(
            "llama-3.3-70b-versatile" to "Llama 3.3 70B (Best quality)",
            "llama-3.1-8b-instant" to "Llama 3.1 8B (Fastest)",
            "mixtral-8x7b-32768" to "Mixtral 8x7B (Good balance)",
            "gemma2-9b-it" to "Gemma 2 9B (Compact)",
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

            val body = response.body?.string() ?: ""

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
                Log.e(TAG, "Groq API error ${response.code}: $errorMsg")
                return@withContext Result.failure(Exception(userMessage))
            }

            val chatResponse = json.decodeFromString<ChatResponse>(body)
            val content = chatResponse.choices.firstOrNull()?.message?.content

            if (content.isNullOrBlank()) {
                return@withContext Result.failure(Exception("AI returned an empty response. Try rephrasing your prompt."))
            }

            Log.d(TAG, "Groq response received (${content.length} chars)")
            Result.success(content)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Request timed out. Groq may be overloaded — try again."))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("No internet connection. Check your network and try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Groq request failed", e)
            Result.failure(Exception("AI request failed: ${e.message}"))
        }
    }
}
