package com.example.first

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Roast {
    suspend fun generateRoast(artists: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY

            // Debug logging
            Log.d("RoastDebug", "API Key length: ${apiKey.length}")
            Log.d("RoastDebug", "API Key first 10 chars: ${apiKey.take(10)}")
            Log.d("RoastDebug", "Artists count: ${artists.size}")

            if (apiKey.isEmpty() || apiKey.isBlank()) {
                Log.e("RoastDebug", "API Key is empty!")
                return@withContext "Error: API Key is not configured"
            }

            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )

            val prompt = """
            You are a brutally honest music critic.
            Roast this person's music taste in a sarcastic, witty, playful way. In not more than 50 words.
            No emojis. No compliments. Be clever and borderline offensive.

            Artists:
            ${artists.joinToString("\n")}
            """.trimIndent()

            Log.d("RoastDebug", "Sending request to Gemini...")
            val response = model.generateContent(prompt)
            val result = response.text ?: "Couldn't come up with a roast… their taste is too bland."

            Log.d("RoastDebug", "Success! Roast received: ${result.take(50)}...")
            result

        } catch (e: Exception) {
            Log.e("RoastDebug", "Error occurred: ${e.javaClass.simpleName}")
            Log.e("RoastDebug", "Error message: ${e.message}")
            Log.e("RoastDebug", "Full stack trace:", e)
            "Error: ${e.message}"
        }
    }
}

class RoastViewModel : ViewModel() {
    var roastText: String = ""
        private set

    fun roast(artists: List<String>, callback: (String) -> Unit) {
        Log.d("RoastDebug", "RoastViewModel.roast() called")
        viewModelScope.launch {
            val result = Roast.generateRoast(artists)
            roastText = result
            callback(result)
        }
    }
}