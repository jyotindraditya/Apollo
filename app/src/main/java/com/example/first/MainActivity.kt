package com.example.first

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    private val CLIENT_ID = "7d2d61ba7ff243ad8ee9e7a4e4e2da53"
    private val REDIRECT_URI = "mood://slickback"
    private val AUTH_URL = "https://accounts.spotify.com/authorize"
    private val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private val SCOPES = "user-top-read playlist-modify-public user-read-private playlist-modify-private user-read-email"

    private var codeVerifier: String? = null
    private var accessToken: String? = null

    private lateinit var roastViewModel: RoastViewModel
    private var artistNames: List<String> = emptyList()

    private val topArtistsLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                artistNames =
                    result.data
                        ?.getStringArrayListExtra("ARTIST_NAMES")
                        ?.toList()
                        ?: emptyList()

                Log.d("MAIN", "Received artists: $artistNames")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spotifyLoginButton = findViewById<Button>(R.id.spotifyLoginButton)
        val viewTopTracksButton = findViewById<Button>(R.id.viewTopTracksButton)
        val viewTopArtistsButton = findViewById<Button>(R.id.viewTopArtistsButton)
        val roastButton: Button = findViewById(R.id.roastButton)
        val roastTextView: TextView = findViewById(R.id.roastTextView)

        roastViewModel = ViewModelProvider(this)[RoastViewModel::class.java]

        roastButton.setOnClickListener {
            if (accessToken == null) {
                Toast.makeText(this, "Please log in first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (artistNames.isEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val artists = getTopArtists(accessToken!!)
                        artistNames = artists.map { it.name }

                        runOnUiThread {
                            if (artistNames.isNotEmpty()) {
                                roastTextView.text = "Cooking your roast… 🔥"
                                roastViewModel.roast(artistNames) { roast ->
                                    roastTextView.text = roast
                                }
                            } else {
                                roastTextView.text = "No artists found. Listen to some music first!"
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            roastTextView.text = "Error fetching artists: ${e.message}"
                            Log.e("MainActivity", "Error fetching artists", e)
                        }
                    }
                }
            } else {
                // Artists already cached, just roast
                roastTextView.text = "Cooking your roast… 🔥"
                roastViewModel.roast(artistNames) { roast ->
                    roastTextView.text = roast
                }
            }
        }

        spotifyLoginButton.setOnClickListener {
            startSpotifyLogin()
        }

        viewTopTracksButton.setOnClickListener {
            if (accessToken != null) {
                val intent = Intent(this, TopActivity::class.java)
                intent.putExtra("SPOTIFY_ACCESS_TOKEN", accessToken)
                intent.putExtra("MODE", "TRACKS")
                startActivity(intent)

            } else {
                Toast.makeText(this, "Please log in first!", Toast.LENGTH_SHORT).show()
            }
        }
        viewTopArtistsButton.setOnClickListener {
            if (accessToken != null) {
                val intent = Intent(this, TopActivity::class.java)
                intent.putExtra("SPOTIFY_ACCESS_TOKEN", accessToken)
                intent.putExtra("MODE", "ARTISTS")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please log in first!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private suspend fun getTopArtists(token: String): List<SpotifyArtist> {
        val url = "https://api.spotify.com/v1/me/top/artists?limit=50&time_range=long_term"

        return withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }

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
    private fun startSpotifyLogin() {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)

        val authUri = Uri.parse(AUTH_URL)
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, authUri)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            if (uri.toString().startsWith(REDIRECT_URI)) {
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")

                if (error != null) {
                    Toast.makeText(this, "Login failed: $error", Toast.LENGTH_SHORT).show()
                } else if (code != null) {
                    exchangeCodeForToken(code)
                }
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(TOKEN_URL)
                val postData = "client_id=$CLIENT_ID" +
                        "&grant_type=authorization_code" +
                        "&code=${Uri.encode(code)}" +
                        "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                        "&code_verifier=${Uri.encode(codeVerifier)}"

                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.write(postData.toByteArray())

                val stream = if (connection.responseCode in 200..299)
                    connection.inputStream
                else
                    connection.errorStream
                val response = stream.bufferedReader().readText()
                val json = JSONObject(response)
                accessToken = json.getString("access_token")
                Log.d("SpotifyDebug", "Token: $accessToken")

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Token exchange failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
