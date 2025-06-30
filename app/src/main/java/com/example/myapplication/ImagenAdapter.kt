package com.example.myapplication

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImagenAdapter(private val imagenes: List<Uri>) :
    RecyclerView.Adapter<ImagenAdapter.ImagenViewHolder>() {

    class ImagenViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_imagen, parent, false)
        val imageView = view.findViewById<ImageView>(R.id.imageViewItem)
        return ImagenViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImagenViewHolder, position: Int) {
        holder.imageView.setImageURI(imagenes[position])
    }

    override fun getItemCount() = imagenes.size
}
