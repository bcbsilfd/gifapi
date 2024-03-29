package com.example.gifapp.ui.adapters

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gifapp.R


class GifViewHolder(view: View): RecyclerView.ViewHolder(view){
    val image : ImageView
    val description: TextView
    val progressBar: ProgressBar

    init {
        view.apply {
            image = findViewById(R.id.gif_iv)
            description = findViewById(R.id.gif_desc_tv)
            progressBar = findViewById(R.id.gif_progressBar)
        }
    }
}