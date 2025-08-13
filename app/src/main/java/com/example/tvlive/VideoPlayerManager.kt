package com.example.tvlive

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueArray
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
            }
        }
    }

    suspend fun run(jsCode: String) {
        withContext(Dispatchers.IO) {
            try {
                // 初始化 Node.js 引擎（耗时操作，放后台）
                v8Runtime = V8Host.getNodeInstance().createV8Runtime()
                setupJsBridge()
                v8Runtime?.getExecutor(jsCode)?.executeVoid()
            } catch (e: JavetException) {
                Log.e("播放管理", "runjs", e)
            }
        }
        // 在子线程初始化 QuickJS 和 fetch
    }

    private suspend fun setupJsBridge() {
        v8Runtime?.let { runtime ->
            runtime.globalObject["sendDataToKotlin"] = { args: Array<V8Value> ->
                if (args.isNotEmpty()) {
                    val jsArray = args[0] as V8ValueArray
                    try {
                        // 2. 将 JS 数组转换为 JSON 字符串
                        val jsonString = jsArray.toJsonString()
                        // 3. 用 Gson 将 JSON 字符串解析为 List<User>
                        val gson = Gson()
                        val type = object : TypeToken<List<Channel>>() {}.type // 声明泛型类型
                        channels = gson.fromJson(jsonString, type)
                        // 转换完成！直接使用 userList
                        // [User(id=1, name=张三), User(id=2, name=李四)]
                    } finally {
                        // 释放资源（必须执行）
                        jsArray.close()
                    }
                    // 如果需要更新UI，切换回主线程
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
