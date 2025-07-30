package com.example.tvlive.model

data class Channel(
    val id: Int,
    val name: String,
    val url: String, // M3U8直播源地址
)
