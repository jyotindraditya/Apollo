package com.example.first

data class SpotifyArtist(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?
)
data class SpotifyImage(
    val url: String,
    val width: Int,
    val height: Int
)
data class Track(
    val name: String,
    val artist: String
)