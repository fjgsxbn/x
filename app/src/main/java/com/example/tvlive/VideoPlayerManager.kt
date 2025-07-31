package com.example.tvlive

import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.withContext
 import okhttp3.OkHttpClient
 import okhttp3.Request
 import java.io.IOException
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
            val client = OkHttpClient()
         val request = Request.Builder()
             .url(addr)
             .build()
         try {
             // 发送同步请求（因在 IO 线程，不会阻塞主线程）
             val response = client.newCall(request).execute()
             // 响应成功且有内容时，返回字符串
             if (response.isSuccessful && response.body != null) {
                 response.body!!.string()
             } else {
                 // 响应失败（如 404、500 等）
                 null
             }
         } catch (e: IOException) {
             // 网络异常（如无网络、连接超时等）
             e.printStackTrace()
             null
         }

            // 2. 如果需要更新 UI，手动切换到主线程（Dispatchers.Main）
            withContext(Dispatchers.Main) {
                callback()
            }
        }

        
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
