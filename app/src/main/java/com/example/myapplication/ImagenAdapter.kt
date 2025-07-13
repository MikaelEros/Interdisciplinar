package com.example.myapplication

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImagenAdapter(private val imagenes: List<Uri>) :
    RecyclerView.Adapter<ImagenAdapter.ImagenViewHolder>() {

    class ImagenViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewItem)
        val btnDescargar: android.widget.Button = itemView.findViewById(R.id.btnDescargar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_imagen, parent, false)
        return ImagenViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImagenViewHolder, position: Int) {
        holder.imageView.setImageURI(imagenes[position])
    }

    override fun getItemCount() = imagenes.size
}
