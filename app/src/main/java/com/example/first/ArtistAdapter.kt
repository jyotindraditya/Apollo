package com.example.first

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ArtistAdapter(private val artists: List<SpotifyArtist>) :
    RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val artistName: TextView = itemView.findViewById(R.id.artistName)
        val artistImage: ImageView = itemView.findViewById(R.id.artistImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.artist_item, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]
        holder.artistName.text = artist.name
        if (!artist.images.isNullOrEmpty()) {
            Glide.with(holder.artistImage.context)
                .load(artist.images[0].url) // The compiler now knows images is not null or empty
                .into(holder.artistImage)
        } else {
            holder.artistImage.setImageResource(R.drawable.ic_artist_placeholder)
        }
    }

    override fun getItemCount(): Int = artists.size
}
