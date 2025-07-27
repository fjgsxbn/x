package com.example.tvlive

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class VideoPlayerManager(context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun getPlayer() = exoPlayer

    // 加载M3U8直播源
    fun playUrl(url: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Android TV Live Player")
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() {
        exoPlayer.release()
    }
}
