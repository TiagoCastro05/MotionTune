package com.example.tiagocastro.motiontune

import java.io.Serializable

data class Music(
    val title: String,
    val artist: String,
    val uriString: String
) : Serializable