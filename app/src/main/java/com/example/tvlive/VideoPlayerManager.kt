package com.example.tvlive

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
//import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.io.IOException

class VideoPlayerManager(context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun getPlayer() = exoPlayer

    data class Channel(val name: String, val url: String)
    private lateinit var channels: List<Channel>

    fun p(adx: String, callback: () -> Unit) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            // 1. 启动协程（默认在主线程，但会被 withContext 切换）
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(adx)
                .build()
            try {
                // 发送同步请求（因在 IO 线程，不会阻塞主线程）
                val response = client.newCall(request).execute()
                // 响应成功且有内容时，返回字符串
                if (response.isSuccessful && response.body != null) {
                    var j = response.body!!.string()
                    r(j)
                    withContext(Dispatchers.Main) {
                        play(0)
                    }
                } else {
                    // 响应失败（如 404、500 等）
                    withContext(Dispatchers.Main) {
                        callback()
                    }
                }
            } catch (e: IOException) {
                // 网络异常（如无网络、连接超时等）
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback()
                }
            }
        }
    }

// 函数名改为小写 r，功能不变
    suspend fun r(jsCode: String) {
        val context = org.mozilla.javascript.Context.Context.enter()
        return try {
            context.optimizationLevel = -1
            val scope: Scriptable = context.initStandardObjects()

            // 执行 JS 代码，预期返回数组
            val jsResult = context.evaluateString(scope, jsCode, "JSCode", 1, null)
            val jsonString = jsResult.toString()

            val gson = Gson()
            channels = gson.fromJson(jsonString, Array<Channel>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            Context.exit()
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
    fun play(num: Int) {
        playUrl(channels[num].url)
    }

    fun release() {
        exoPlayer.release()
    }
}
