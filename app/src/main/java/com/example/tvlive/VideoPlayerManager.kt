package com.example.tvlive

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.IV8ValuePromise
import com.caoccao.javet.values.reference.IV8ValuePromise.IListener
import com.caoccao.javet.values.reference.V8ValueError
import com.caoccao.javet.values.reference.V8ValueObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*

class VideoPlayerManager(private val context: AppCompatActivity, private val webView: WebView) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .build()

    fun getPlayer() = exoPlayer

    data class Channel(val name: String, val url: String)
    private val v8Runtime: V8Runtime = V8Host.getNodeInstance().createV8Runtime()

    @Volatile
    var channels: List<Channel> = mutableListOf()

    @Volatile
    var num: Int? = null

    fun p(adx: String, callback: () -> Unit) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(adx).build()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, adx, Toast.LENGTH_SHORT).show()
            }
            try {
                // 发送同步请求（因在 IO 线程，不会阻塞主线程）
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, response.toString(), Toast.LENGTH_SHORT).show()
                }

                if (response.isSuccessful && response.body != null) {
                    var js = response.body!!.string()
                    Log.i("订阅", js)
                    withContext(Dispatchers.Main) {
                        val toast = Toast.makeText(context, "订阅js" + js, Toast.LENGTH_SHORT)
                        toast.show()
                    }

                    run(js)
                    withContext(Dispatchers.Main) {
                        if (channels.size != 0) {
                            play(0)
                        }
                    }
                } else {
                    // 响应失败（如 404、500 等）
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "订阅地址请求失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                // 网络异常（如无网络、连接超时等）
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message + e.javaClass.name, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IllegalArgumentException) {
                // 处理请求参数错误
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message + e.javaClass.name, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                Log.e("播放管理", "兜底", e)
            }
        }
    }

    suspend fun run(jsCode: String) {
        withContext(Dispatchers.IO) {
            try {
                val xtv = Xtv()
                val v8ValueObject: V8ValueObject = v8Runtime!!.createV8ValueObject()
                v8Runtime.globalObject!!.set("xtv", v8ValueObject)
                v8ValueObject.bind(xtv)
                v8Runtime.getExecutor(jsCode).executeVoid()
                // launch {
                //   while (true) {
                //       v8Runtime!!.await()
                //      delay(10000)
                //    }
                // }
                v8Runtime.await()
            } catch (e: Exception) {
                Log.e("播放管理", "runjs", e)
            }
        }
    }

    // 加载M3U8直播源
    fun playUrl(url: String) {
        // 1. 创建媒体项（MediaItem）：封装 M3U8 直播地址
        val mediaItem = MediaItem.fromUri(url)
        // 2. 创建 HLS 媒体源（专门解析 M3U8 格式，支持直播分段拉流）
        val hlsMediaSource = HlsMediaSource.Factory(
            // 配置网络数据源：支持 HTTP 请求头鉴权、超时设置
            DefaultHttpDataSource.Factory()
                .setUserAgent("Media3-Live-Player/1.7.1") // 设置 User-Agent（部分服务器校验）
                .setConnectTimeoutMs(10000) // 连接超时：10 秒
                .setReadTimeoutMs(10000) // 读取超时：10 秒
                .setDefaultRequestProperties(
                    // 可选：添加直播鉴权请求头（如 Token、Cookie）
                    mapOf(
                        "Authorization" to "Bearer your-live-token",
                        "X-Live-Id" to "123456"
                    )
                )
        )
            .setAllowChunklessPreparation(true) // 无缓冲快速启动（直播首屏加载更快）
            .createMediaSource(mediaItem)
        exoPlayer.setMediaSource(hlsMediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }
    fun play(num: Int) {
        playUrl(channels[num].url)
        this.num = num
    }
    fun playPrev() {
        if (num == null) {
            return
        }
        var tar = num!! - 1
        if (tar < 0) {
            tar = channels.size - 1
        }
        play(tar)
    }

    fun playNext() {
        if (num == null) {
            return
        }
        var tar = num!! + 1
        if (tar >= channels.size) {
            tar = 0
        }
        play(tar)
    }

    fun release() {
        exoPlayer.release()
        context.lifecycleScope.launch(Dispatchers.IO) {
            v8Runtime?.close()
        }
    }

    inner class Xtv {
        @V8Function(name = "update")
        suspend fun update(json: String) {
            Log.i("json", json)
            val gson = Gson()

            // 关键：通过 TypeToken 指定泛型类型 List<Channel>
            val type = object : TypeToken<List<Channel>>() {}.type

            // 直接解析为 List<Channel>

            if (channels.size == 0) {
                channels = gson.fromJson(json, type)
                context.lifecycleScope.launch {
                    if (channels.size != 0) {
                        play(0)
                    }
                }
            }
        }
    }
    private val callback = object : IV8ValuePromise.IListener {
        override fun onCatch(v8Value: V8Value) {
            // 处理 Promise 内部未捕获的异常（如 JS 代码报错）
            // assertTrue(v8Value is V8ValueError)
            val error = v8Value as V8ValueError
            println("Promise 捕获异常：${error.message}")
            v8Value.close() // 释放 V8 资源（避免内存泄漏）
        }
        override fun onFulfilled(v8Value: V8Value) {
            // 处理 Promise 成功（resolve 触发）
            Log.i("收到promise", v8Value.toString())
            val gson = Gson()

            // 关键：通过 TypeToken 指定泛型类型 List<Channel>
            val type = object : TypeToken<List<Channel>>() {}.type

            // 直接解析为 List<Channel>
            channels = gson.fromJson(v8Value.toString(), type)
            v8Value.close() // 释放资源
        }
        override fun onRejected(v8Value: V8Value) {
            // 处理 Promise 主动失败（reject 触发）
            println("Promise 主动拒绝，原因：$v8Value")
            v8Value.close() // 释放资源
        }
    }
}
