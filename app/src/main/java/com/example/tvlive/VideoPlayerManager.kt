package com.example.tvlive

import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
                    delay(10000)
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
            Toast.makeText(context, "js", Toast.LENGTH_SHORT).show()
            val webSettings: WebSettings = webView.settings
            webSettings.javaScriptEnabled = true // 必须开启JS支持
            webSettings.domStorageEnabled = true // 可选：启用DOM存储（部分JS功能需要）
            // 配置WebViewClient，避免跳转系统浏览器
            // webView.webViewClient = WebViewClient()
            // 可选：配置WebChromeClient（处理JS弹窗等）
            // webView.webChromeClient = WebChromeClient()
            webView.addJavascriptInterface(AndroidCallback(), "AndroidCallback")
            // 用空HTML容器包裹JS代码（确保JS能被WebView执行）
            val jsWrapper = """
             <html>
                 <script>$jsCode</script>
             </html>
            """.trimIndent()
            // 加载仅包含JS的HTML（无任何可视内容）
            webView.loadDataWithBaseURL(null, jsWrapper, "text/html", "UTF-8", null)
        }
    }

    // 原生回调接口类
    inner class AndroidCallback {
        @JavascriptInterface
        fun onTimerUpdate(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            val gson = Gson()
            // 由于Kotlin泛型擦除，需要通过TypeToken指定List<Channel>类型
            val type = object : TypeToken<List<Channel>>() {}.type
            // 转换JSON字符串为List<Channel>
            channels = gson.fromJson(message, type)
            // 打印结果
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
