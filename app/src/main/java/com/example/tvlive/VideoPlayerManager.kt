package com.example.tvlive

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.net.DefaultHttpDataSource
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
        .setVideoRendererEventListener(object : VideoRendererEventListener {
            // 监听视频渲染相关事件（直接关联画面状态）
            override fun onFirstFrameRendered() {
                Log.d(TAG, "画面首次渲染成功")
            }
            override fun onVideoSizeChanged(
                videoSize: VideoSize,
                rotationDegrees: Int,
                pixelWidthHeightRatio: Float
            ) {
                Log.d(TAG, "视频尺寸变化：${videoSize.width}x${videoSize.height}")
            }
            override fun onRenderedFirstFrame(surface: Any) {
                Log.d(TAG, "Surface 首次渲染画面")
            }
            override fun onVideoDecoderInitialized(
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                Log.d(TAG, "视频解码器初始化：$decoderName")
            }
            override fun onVideoDecoderReleased(decoderName: String) {
                Log.d(TAG, "视频解码器释放：$decoderName")
            }
            override fun onVideoInputFormatChanged(format: Format) {
                Log.d(TAG, "视频输入格式变化：${format.codecs}")
            }
            override fun onDroppedFrames(count: Int, elapsedMs: Long) {
                // 关键：画面丢帧过多可能导致停滞，此处需重点关注
                Log.e(TAG, "画面丢帧！丢帧数量：$count，耗时：$elapsedMs ms")
                if (count > 10) { // 丢帧阈值可根据需求调整
                    Log.e(TAG, "严重丢帧，可能导致画面停止")
                }
            }
        }).build()

    init {
        // 2. 添加核心播放事件监听（捕获错误、进度异常）
        exoPlayer?.addListener(object : Player.EventListener {
            // 监听播放状态变化（如播放/暂停/停止）
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                val stateDesc = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE（空闲）"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING（缓冲中）"
                    Player.STATE_READY -> "STATE_READY（可播放）"
                    Player.STATE_ENDED -> "STATE_ENDED（播放结束）"
                    else -> "未知状态"
                }
                Log.d(TAG, "播放状态变化：$stateDesc")
                // 当状态为「可播放」时，开始监测进度与画面是否同步
                if (playbackState == Player.STATE_READY && exoPlayer?.isPlaying == true) {
                    // startPlaybackPositionMonitor()
                }
            }

            // 监听播放错误（核心：直接捕获 ExoPlayer 异常）
            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)
                Log.e(TAG, "=== 播放错误捕获 ===")
                // 细分错误类型（针对性定位问题）
                when (error.type) {
                    // 1. 源错误（网络、媒体文件损坏、格式不支持等）
                    ExoPlaybackException.TYPE_SOURCE -> {
                        Log.e(TAG, "错误类型：源错误（网络/文件问题）")
                        error.sourceException?.let { sourceEx ->
                            Log.e(TAG, "源错误详情：${sourceEx.message}", sourceEx)
                            // 若为 HTTP 错误（如 404、500），可进一步判断
                            if (sourceEx is HttpDataSource.HttpDataSourceException) {
                                Log.e(TAG, "HTTP 错误码：${sourceEx.responseCode}")
                            }
                        }
                    }
                    // 2. 渲染错误（视频解码失败、画面渲染异常等，与「画面停声音正常」强相关）
                    ExoPlaybackException.TYPE_RENDERER -> {
                        Log.e(TAG, "错误类型：渲染错误（画面/解码问题）")
                        error.rendererException?.let { renderEx ->
                            Log.e(TAG, "渲染错误详情：${renderEx.message}", renderEx)
                            // 常见渲染错误：解码器初始化失败、不支持的编码格式
                            if (renderEx is DecoderInitializationException) {
                                Log.e(TAG, "解码器初始化失败：不支持的编码？${renderEx.decoderName}")
                            }
                        }
                    }
                    // 3. 其他错误（播放器内部逻辑、资源不足等）
                    ExoPlaybackException.TYPE_UNEXPECTED -> {
                        Log.e(TAG, "错误类型：意外错误（内部逻辑/资源问题）")
                        Log.e(TAG, "意外错误详情：${error.unexpectedException?.message}", error.unexpectedException)
                    }
                }
            }

            // 监听播放进度（间接判断画面是否停滞：进度在走但画面不动）
            override fun onPlaybackPositionChanged(
                positionMs: Long,
                bufferedPositionMs: Long,
                playWhenReady: Boolean,
                playbackState: Int
            ) {
                super.onPlaybackPositionChanged(positionMs, bufferedPositionMs, playWhenReady, playbackState)
                // 更新最新播放进度（用于监测）
                // lastPlaybackPositionMs = positionMs
            }
        })
        // 3. 可选：添加 ExoPlayer 内置日志工具（更详细的事件时序）
        val eventLogger = EventLogger()
        exoPlayer?.addListener(eventLogger)
        // 若需输出视频渲染日志，需额外添加 VideoRendererEventListener
        exoPlayer?.videoRendererEventListener = eventLogger
    }

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
