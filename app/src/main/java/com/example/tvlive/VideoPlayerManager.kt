package com.example.tvlive

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class VideoPlayerManager(context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun getPlayer() = exoPlayer

    fun p(addr: String, callback: () -> Unit) {
        context.lifecycleScope.launch {
            // 1. 启动协程（默认在主线程，但会被 withContext 切换）
            val result = fetchUrlAsString("https://www.example.com")

            // 2. 如果需要更新 UI，手动切换到主线程（Dispatchers.Main）
            withContext(Dispatchers.Main) {
                textView.text = if (result != null) "请求成功" else "请求失败"
            }
        }

        callback()
    }

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
