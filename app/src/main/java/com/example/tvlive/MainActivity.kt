package com.example.tvlive

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.tvlive.model.Channel
import com.google.android.exoplayer2.ui.StyledPlayerView

class MainActivity : AppCompatActivity() {
    private lateinit var playerView: StyledPlayerView
    private lateinit var playerManager: VideoPlayerManager
    private val channels = listOf(
        Channel(1, "央视一套", "http://hbsz.chinashadt.com:2036/live/stream:sztv.stream/playlist.m3u8"),
        Channel(2, "央视新闻", "https://0472.org/hls/cgtn.m3u8"),
        //Channel(3, "北京卫视", "https://btv.btime.com/hls/btv1.m3u8"),
        //Channel(4, "湖南卫视", "https://hunantv.cdn.hunantv.com/hls/hunantv.m3u8")
        // 可添加更多频道
    )
    private var currentChannelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化播放器
        playerManager = VideoPlayerManager(this)
        playerView = findViewById(R.id.player_view)
        playerView.player = playerManager.getPlayer()
        playCurrentChannel()
    }

    private fun playCurrentChannel() {
        playerManager.playUrl(channels[currentChannelIndex].url)
    }

