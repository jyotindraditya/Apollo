package com.example.first

import android.content.Intent
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
import java.net.HttpURLConnection
import java.net.URL

class TopActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var tracksRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("MODE")
        val token = intent.getStringExtra("SPOTIFY_ACCESS_TOKEN")

        if (token == null) {
            Toast.makeText(this, "Error: Authentication token not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (mode == "ARTISTS") {
            setContentView(R.layout.activity_top_artists)
        } else {
            setContentView(R.layout.activity_top_tracks)
        }


        tracksRecyclerView = findViewById(R.id.tracksRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        tracksRecyclerView.layoutManager = LinearLayoutManager(this)
        tracksRecyclerView.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                this,
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            )
        )

        if (mode == "ARTISTS") {
            progressBar.visibility = View.VISIBLE
            loadTopArtists(token)
        } else {
            progressBar.visibility = View.VISIBLE
            loadTopTracks(token)
        }
    }

    private fun loadTopTracks(token: String) {
        progressBar.visibility = View.VISIBLE

        // Use lifecycleScope to launch a coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.spotify.com/v1/me/top/tracks?limit=50&time_range=long_term")
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
                            this@TopActivity,
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
                        this@TopActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    suspend fun getTopArtists(token: String): List<SpotifyArtist> {
        val url = "https://api.spotify.com/v1/me/top/artists?limit=50&time_range=long_term"

        return withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            Log.d("GEMINI", "Key = ${BuildConfig.GEMINI_API_KEY}")


            try {
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val items = json.getJSONArray("items")

                    val result = mutableListOf<SpotifyArtist>()

                    for (i in 0 until items.length()) {
                        val obj = items.getJSONObject(i)

                        val id = obj.getString("id")
                        val name = obj.getString("name")

                        val imagesJson = obj.optJSONArray("images")
                        val images = mutableListOf<SpotifyImage>()

                        if (imagesJson != null) {
                            for (j in 0 until imagesJson.length()) {
                                val img = imagesJson.getJSONObject(j)
                                images.add(
                                    SpotifyImage(
                                        height = img.optInt("height"),
                                        width = img.optInt("width"),
                                        url = img.getString("url")
                                    )
                                )
                            }
                        }
                        result.add(
                            SpotifyArtist(
                                id = id,
                                name = name,
                                images = images
                            )
                        )
                    }
                    result
                } else {
                    emptyList()
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    fun loadTopArtists(token: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val artists = getTopArtists(token)
            progressBar.visibility = View.GONE

            tracksRecyclerView.adapter = ArtistAdapter(artists)

            // Check if we need to return data to MainActivity
            val shouldReturnData = intent.getBooleanExtra("RETURN_DATA", false)
            if (shouldReturnData) {
                returnArtistsAndFinish(artists)
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
            Log.e("TopActivity", "Failed to parse JSON", e)
        }
        return tracks
    }
    private fun returnArtistsAndFinish(artists: List<SpotifyArtist>) {
        val intent = Intent()
        intent.putStringArrayListExtra(
            "ARTIST_NAMES",
            ArrayList(artists.map { it.name })
        )
        setResult(RESULT_OK, intent)
        finish()
    }
}