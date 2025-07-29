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
        Channel(3, "北京卫视", "https://btv.btime.com/hls/btv1.m3u8"),
        Channel(4, "湖南卫视", "https://hunantv.cdn.hunantv.com/hls/hunantv.m3u8")
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

    private fun switchToChannel(index: Int) {
        currentChannelIndex = index.coerceIn(0, channels.size - 1)
        playCurrentChannel()
    }

    // 按键监听
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            // 菜单键/确定键调出列表
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_CENTER -> {
                showChannelList()
                true
            }
            // 上键切换上一个频道
            KeyEvent.KEYCODE_DPAD_UP -> {
                switchToChannel(if(currentChannelIndex=channels.size-1) 0 else currentChannelIndex + 1)
                true
            }
            // 下键切换下一个频道
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                switchToChannel(if(currentChannelIndex=0) channels.size-1 else currentChannelIndex - 1)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish() // 按返回键退出
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showChannelList() {
        ChannelListDialog(this, channels, currentChannelIndex) { position ->
            switchToChannel(position)
        }.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}
