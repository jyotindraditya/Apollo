package com.example.first

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

class TopTracksActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var tracksRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_tracks)

        tracksRecyclerView = findViewById(R.id.tracksRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        tracksRecyclerView.layoutManager = LinearLayoutManager(this)

        tracksRecyclerView.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                this,
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            )
        )


        // Get the access token passed from MainActivity
        val accessToken = intent.getStringExtra("SPOTIFY_ACCESS_TOKEN")

        if (accessToken != null) {
            fetchTopTracks(accessToken)
        } else {
            Log.e("TopTracksActivity", "Access token is missing!")
            Toast.makeText(this, "Error: Authentication token not found.", Toast.LENGTH_LONG).show()
            finish() // Close the activity if there's no token
        }
    }

    private fun fetchTopTracks(token: String) {
        progressBar.visibility = View.VISIBLE

        // Use lifecycleScope to launch a coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.spotify.com/v1/me/top/tracks?limit=10&time_range=long_term")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val tracks = parseTracks(responseData)
                    // Switch back to the main thread to update the UI
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        tracksRecyclerView.adapter = TrackAdapter(tracks)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Log.e("TopTracksActivity", "API Error: ${response.message}")
                        Toast.makeText(
                            this@TopTracksActivity,
                            "Failed to fetch tracks.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Log.e("TopTracksActivity", "Network call failed", e)
                    Toast.makeText(
                        this@TopTracksActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun parseTracks(jsonData: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val jsonObject = JSONObject(jsonData)
            val items = jsonObject.getJSONArray("items")
            for (i in 0 until items.length()) {
                val trackObject = items.getJSONObject(i)
                val trackName = trackObject.getString("name")
                // Artists are in an array, we'll take the first one.
                val artistName =
                    trackObject.getJSONArray("artists").getJSONObject(0).getString("name")
                tracks.add(Track(trackName, artistName))
            }
        } catch (e: JSONException) {
            Log.e("TopTracksActivity", "Failed to parse JSON", e)
        }
        return tracks
    }
}