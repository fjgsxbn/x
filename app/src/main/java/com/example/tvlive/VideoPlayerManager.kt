package com.example.tvlive

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.IV8ValuePromise
import com.caoccao.javet.values.reference.V8ValueError
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
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

                    withContext(Dispatchers.Main) {
                        callback()
                    }
                }
            } catch (e: IOException) {
                // 网络异常（如无网络、连接超时等）
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message + e.javaClass.name, Toast.LENGTH_SHORT).show()
                }

                withContext(Dispatchers.Main) {
                    callback()
                }
            } catch (e: IllegalArgumentException) {
                // 处理请求参数错误
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message + e.javaClass.name, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("播放管理", "兜底", e)
            }
        }
    }

    suspend fun run(jsCode: String) {
        withContext(Dispatchers.IO) {
            try {
                // 初始化 Node.js 引擎（耗时操作，放后台）
                v8Runtime = V8Host.getNodeInstance().createV8Runtime()
                // setupJsBridge()
                // v8Runtime?.getExecutor(jsCode)?.executeVoid()
                v8Runtime!!.getExecutor(jsCode)
                    .execute()?.use { v8ValuePromise -> // Kotlin use 函数：自动关闭 V8ValuePromise（释放资源）
                        v8ValuePromise.register(callback) // 注册回调
                        v8Runtime.await() // 等待 Promise 完成（阻塞到回调触发）
                    }
            } catch (e: Exception) {
                Log.e("播放管理", "runjs", e)
            }
        }
        // 在子线程初始化 QuickJS 和 fetch
    }

    private suspend fun setupJsBridge() {
        // 2. 配置转换器，支持 Kotlin 与 JS 互操作
        // val converter = JavetProxyConverter()
        // v8Runtime?.setConverter(converter)
        // 3. 创建 Kotlin 接收者实例，并注入到 Node.js 全局对象
        val xtv = Xtv()
        v8Runtime!!.globalObject!!.set("xtv", xtv)
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
    inner class Xtv {
        // @V8Function
        fun update(json: String) {
            Log.i("json", json)
            val gson = Gson()

            // 关键：通过 TypeToken 指定泛型类型 List<Channel>
            val type = object : TypeToken<List<Channel>>() {}.type

            // 直接解析为 List<Channel>
            channels = gson.fromJson(json, type)
        }
    }
    private val callback = object : IV8ValuePromise.ICallback {
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
            v8Value.close() // 释放资源
        }
        override fun onRejected(v8Value: V8Value) {
            // 处理 Promise 主动失败（reject 触发）
            println("Promise 主动拒绝，原因：$v8Value")
            v8Value.close() // 释放资源
        }
    }
}
