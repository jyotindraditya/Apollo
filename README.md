<h1>About=></h1>
Apollo is a Kotlin-based Android application that authenticates users via Spotify's OAuth 2.0 PKCE flow and retrieves their top 50 tracks and artists using the Spotify Web API.
The app then passes this listening data to Google's Gemini 2.5 Flash AI model, which generates humorous, personalized roasts of the user's music taste. Built with MVVM architecture, Kotlin Coroutines for asynchronous operations, and RecyclerView for data presentation.

<h2>Current status=></h2>
Due to current spotify API policies, we can't use extended quota mode until until it is being used for a large audience by an organization. So we are stuck in development mode ie. I will have to manually put the email ids of the users to allow them to use this app.
