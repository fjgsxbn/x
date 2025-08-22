package com.example.tvlive

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.DecoderInitializationException
import androidx.media3.common.ExoPlaybackException
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.EventLogger
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.net.DefaultHttpDataSource
import androidx.media3.ui.VideoRendererEventListener
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.IV8ValuePromise
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

class VideoPlayerManager(private val context: AppCompatActivity, private val webView: WebView) {
    // 1. 新增 TAG 常量，解决 Log 日志未定义问题
    private val TAG = "VideoPlayerManager"

    // 2. 修正 ExoPlayer 初始化：适配 Media3 1.7.1 VideoRendererEventListener 接口
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setVideoRendererEventListener(object : VideoRendererEventListener {
            override fun onFirstFrameRendered(surface: Any) {
                Log.d(TAG, "画面首次渲染成功")
            }

            override fun onVideoSizeChanged(
                videoSize: VideoSize,
                rotationDegrees: Int,
                pixelWidthHeightRatio: Float
            ) {
                Log.d(TAG, "视频尺寸变化：${videoSize.width}x${videoSize.height}")
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
                Log.e(TAG, "画面丢帧！丢帧数量：$count，耗时：$elapsedMs ms")
                if (count > 10) {
                    Log.e(TAG, "严重丢帧，可能导致画面停止")
                }
            }
        })
        .build()

    init {
        // 3. 修正 Player.Listener 接口（删除 Event 后缀，适配 Media3 规范）
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateDesc = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE（空闲）"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING（缓冲中）"
                    Player.STATE_READY -> "STATE_READY（可播放）"
                    Player.STATE_ENDED -> "STATE_ENDED（播放结束）"
                    else -> "未知状态"
                }
                Log.d(TAG, "播放状态变化：$stateDesc")
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                Log.e(TAG, "=== 播放错误捕获 ===")
                when (error.type) {
                    ExoPlaybackException.TYPE_SOURCE -> {
                        Log.e(TAG, "错误类型：源错误（网络/文件问题）")
                        error.sourceException?.let { sourceEx ->
                            Log.e(TAG, "源错误详情：${sourceEx.message}", sourceEx)
                            if (sourceEx is HttpDataSource.HttpDataSourceException) {
                                Log.e(TAG, "HTTP 错误码：${sourceEx.responseCode}")
                            }
                        }
                    }
                    ExoPlaybackException.TYPE_RENDERER -> {
                        Log.e(TAG, "错误类型：渲染错误（画面/解码问题）")
                        error.rendererException?.let { renderEx ->
                            Log.e(TAG, "渲染错误详情：${renderEx.message}", renderEx)
                            if (renderEx is DecoderInitializationException) {
                                Log.e(TAG, "解码器初始化失败：不支持的编码？${renderEx.decoderName}")
                            }
                        }
                    }
                    ExoPlaybackException.TYPE_UNEXPECTED -> {
                        Log.e(TAG, "错误类型：意外错误（内部逻辑/资源问题）")
                        Log.e(TAG, "意外错误详情：${error.unexpectedException?.message}", error.unexpectedException)
                    }
                }
            }

            override fun onPlaybackPositionChanged(
                positionMs: Long,
                bufferedPositionMs: Long,
                playWhenReady: Boolean,
                playbackState: Int
            ) {
                // 进度监听逻辑（如需使用可补充）
            }
        })

        // 4. 修正 EventLogger 初始化（适配 Media3 1.7.1）
        val eventLogger = EventLogger()
        exoPlayer.addListener(eventLogger)
        exoPlayer.videoRendererEventListener = eventLogger
    }

    // 获取 ExoPlayer 实例的方法
    fun getPlayer() = exoPlayer

    // 频道数据类
    data class Channel(val name: String, val url: String)

    // V8Runtime 初始化（确保资源后续会释放）
    private val v8Runtime: V8Runtime = V8Host.getNodeInstance().createV8Runtime()

    // volatile 修饰的成员变量（线程安全）
    @Volatile
    var channels: List<Channel> = mutableListOf()

    @Volatile
    var num: Int? = null

    // 5. 补全 p 函数括号，修复语法错误，补充响应处理和异常捕获
    fun p(adx: String, callback: () -> Unit) {
        context.lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(adx).build()
            
            // 主线程显示 Toast
            withContext(Dispatchers.Main) {
                Toast.makeText(context, adx, Toast.LENGTH_SHORT).show()
            }

            try {
                // 发送网络请求（IO 线程，不阻塞主线程）
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // 解析响应（示例：假设响应是频道列表 JSON）
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "请求成功，响应内容：$responseBody")
                        // （可选）Gson 解析 JSON 为 Channel 列表
                        runCatching {
                            channels = Gson().fromJson(
                                responseBody,
                                object : TypeToken<List<Channel>>() {}.type
                            )
                        }.onFailure {
                            Log.e(TAG, "JSON 解析失败", it)
                            Toast.makeText(context, "数据解析失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "请求失败：${response.code}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "请求失败，响应码：${response.code}")
                    }
                    // 执行回调（主线程）
                    callback()
                }
            } catch (e: IOException) {
                // 捕获网络异常
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "网络错误：${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "网络请求异常", e)
                }
            } finally {
                // 关闭响应体，避免资源泄漏
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    // 6. 新增资源释放方法（避免内存泄漏，如 V8Runtime、ExoPlayer）
    fun release() {
        // 释放 ExoPlayer
        exoPlayer.stop()
        exoPlayer.release()
        // 释放 V8Runtime
        runCatching {
            v8Runtime.close()
        }.onFailure {
            Log.e(TAG, "V8Runtime 释放失败", it)
        }
    }

    // 7. 播放 M3U8 直播流的方法（基于之前的逻辑优化）
    fun playUrl(url: String) {
        // 避免空指针和状态冲突
        if (exoPlayer.playbackState in listOf(Player.STATE_BUFFERING, Player.STATE_PREPARING)) {
            exoPlayer.stop()
        }

        // 构建 HTTP 数据源（支持鉴权和超时配置）
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Media3-Live-Player/1.7.1")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
            .setDefaultRequestProperties(
                mapOf(
                    "Authorization" to "Bearer your-live-token",
                    "X-Live-Id" to "123456"
                )
            )

        // 构建 HLS 媒体源（适配 M3U8 格式）
        val mediaItem = MediaItem.fromUri(url)
        val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .setAllowChunklessPreparation(true) // 直播快速启动
            .createMediaSource(mediaItem)

        // 设置媒体源并播放
        exoPlayer.setMediaSource(hlsMediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }
}

