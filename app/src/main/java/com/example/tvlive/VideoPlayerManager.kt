package com.example.tvlive

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.javascript.Scriptable
import java.lang.Exception
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory // 修正点：添加 SSLSocketFactory 导入
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier // 修正点：添加 HostnameVerifier 导入

class VideoPlayerManager(private val context: AppCompatActivity) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun getPlayer() = exoPlayer

    data class Channel(val name: String, val url: String)
    private lateinit var channels: List<Channel>

    fun p(adx: String, callback: () -> Unit) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            // 1. 启动协程（默认在主线程，但会被 withContext 切换）

            // 创建忽略证书验证的 OkHttpClient
            val client = OkHttpClient.Builder()
                .sslSocketFactory(
                    SSLContext.getInstance("TLS").apply {
                        init(null, arrayOf(TrustAllCerts()), SecureRandom())
                    }.socketFactory,
                    TrustAllCerts(),
                )
                .hostnameVerifier { _, _ -> true } // 忽略主机名验证
                .build()

// 辅助类：信任所有证
private class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

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
                        play(0)
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
        val context2 = org.mozilla.javascript.Context.enter()
        try {
            context2.optimizationLevel = -1
            val scope: Scriptable = context2.initStandardObjects()

            // 执行 JS 代码，预期返回数组
            val jsResult = context2.evaluateString(scope, jsCode, "JSCode", 1, null)
            val jsonString = jsResult.toString()
            Toast.makeText(context, "json" + jsonString, Toast.LENGTH_SHORT).show()

            val gson = Gson()
            channels = gson.fromJson(jsonString, Array<Channel>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            org.mozilla.javascript.Context.exit()
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
