package com.example.tiagocastro.motiontune

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.media.MediaMetadataRetriever

class MusicAdapter(
    private val songs: List<Music>,
    private val onClick: (Music, Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val artist: TextView = view.findViewById(R.id.textArtist)
        val image: ImageView = view.findViewById(R.id.imgAlbum)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        // MUDANÇA: Agora usa o teu layout item_music que tem a linha cinzenta
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title
        holder.artist.text = song.artist

        // Tentar carregar a imagem da música
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(holder.itemView.context, Uri.parse(song.uriString))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                holder.image.setImageBitmap(bitmap)
            } else {
                holder.image.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            retriever.release()
        } catch (e: Exception) {
            holder.image.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onClick(song, position) }
    }

    override fun getItemCount() = songs.size
}