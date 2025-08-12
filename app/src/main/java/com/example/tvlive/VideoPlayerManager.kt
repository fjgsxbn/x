package com.example.tvlive

import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.runtime.V8Runtime
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Exception
import java.util.*

class VideoPlayerManager(private val context: AppCompatActivity, private val webView: WebView) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun getPlayer() = exoPlayer

    data class Channel(val name: String, val url: String)

    private var channels: List<Channel> = mutableListOf()
    private var v8Runtime: V8Runtime? = null

    fun p(adx: String, callback: () -> Unit) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            // 1. 启动协程（默认在主线程，但会被 withContext 切换）

            // 创建忽略证书验证的 OkHttpClient
            val client = OkHttpClient.Builder()
                .build()

            val request = Request.Builder()
                .url(adx)
                .build()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, adx, Toast.LENGTH_SHORT).show()
            }
            try {
                // 发送同步请求（因在 IO 线程，不会阻塞主线程）
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, response.toString(), Toast.LENGTH_SHORT).show()
                }
                // 响应成功且有内容时，返回字符串
                if (response.isSuccessful && response.body != null) {
                    var j = response.body!!.string()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "响应" + j, Toast.LENGTH_SHORT).show()
                    }

                    r(j)
                    withContext(Dispatchers.Main) {
                        if (channels.size != 0) {
                            play(0)
                        }
                    }
                } else {
                    // 响应失败（如 404、500 等）
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "404", Toast.LENGTH_SHORT).show()
                    }

                    delay(10000)
                    withContext(Dispatchers.Main) {
                        callback()
                    }
                }
            } catch (e: Exception) {
                // 网络异常（如无网络、连接超时等）
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message + e.javaClass.name, Toast.LENGTH_SHORT).show()
                }
                delay(10000)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback()
                }
            }
        }
    }

// 函数名改为小写 r，功能不变
    suspend fun r(jsCode: String) {
        withContext(Dispatchers.Main) {
            try {
                // 初始化 Node.js 引擎（耗时操作，放后台）
                v8Runtime = V8Host.getNodeInstance().createV8Runtime()
                setupJsBridge()
                v8Runtime?.executeString(jsCode)
            } catch (e: JavetException) {
                Log.e("JsTask", "初始化失败：${e.message}")
            }
        }
        // 在子线程初始化 QuickJS 和 fetch
    }

    private fun setupJsBridge() {
        v8Runtime?.let { runtime ->
            runtime.globalObject["sendDataToKotlin"] = { args ->
                if (args.isNotEmpty()) {
                    val jsList = args[0]
                    val kotlinList = mutableListOf<String>()
                    for (i in 0 until jsList.length) {
                        kotlinList.add(jsList.get(i).toString())
                    }
                    // 如果需要更新UI，切换回主线程
                    lifecycleScope.launch(Dispatchers.Main) {
                        Log.d("JsTask", "Kotlin收到数据：$kotlinList")
                        // 这里可以更新UI，如刷新列表等
                    }
                }
                null
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
    fun play(num: Int) {
        playUrl(channels[num].url)
    }

    fun release() {
        exoPlayer.release()
        context.lifecycleScope.launch(Dispatchers.IO) {
            v8Runtime?.close()
            v8Runtime = null
        }
    }
}
