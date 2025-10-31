package com.example.first

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val SCOPES = "user-top-read user-read-private"

    private var codeVerifier: String? = null
    private var accessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spotifyLoginButton = findViewById<Button>(R.id.spotifyLoginButton)
        val viewTopTracksButton = findViewById<Button>(R.id.viewTopTracksButton)
        val moodSpinner = findViewById<Spinner>(R.id.moodSpinner)
        val moodResultTextView = findViewById<TextView>(R.id.moodResultTextView)

        viewTopTracksButton.isEnabled = false

        // Setup mood spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.moods_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            moodSpinner.adapter = adapter
        }

        moodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                moodResultTextView.text =
                    if (pos > 0) "Selected Mood: ${parent.getItemAtPosition(pos)}" else ""
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spotifyLoginButton.setOnClickListener {
            startSpotifyLogin()
        }

        viewTopTracksButton.setOnClickListener {
            if (accessToken != null) {
                val intent = Intent(this, TopTracksActivity::class.java)
                intent.putExtra("SPOTIFY_ACCESS_TOKEN", accessToken)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please log in first!", Toast.LENGTH_SHORT).show()
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
                val postData =
                    "client_id=$CLIENT_ID&grant_type=authorization_code&code=$code&redirect_uri=$REDIRECT_URI&code_verifier=$codeVerifier"

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.write(postData.toByteArray())

                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                accessToken = json.getString("access_token")

                Log.d("SpotifyAuth", "Access Token: $accessToken")

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.viewTopTracksButton).isEnabled = true
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
