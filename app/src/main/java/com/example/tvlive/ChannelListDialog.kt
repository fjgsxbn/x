package com.example.tvlive

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView

class ChannelListDialog(
    context: Context,
    private val playManager: VideoPlayerManager
) : Dialog(context) {

    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_channel_list)
        window?.apply {
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setGravity(android.view.Gravity.LEFT) // 列表显示在底部
        }

        listView = findViewById(R.id.lv_channels)
        val adapter = ArrayAdapter(
            context,
            R.layout.item_channel,
            playManager.channels.map { it.name }
        )
        listView.adapter = adapter
        if (playManager != null) {
        }
        listView.setSelection(playManager.num)
        listView.requestFocus()

        // 列表项点击事件
        listView.setOnItemClickListener { _, _, position, _ ->
            playManager.play(position)
            dismiss()
        }

        // 遥控器按键监听
        var selectedPosition = playManager.num
        listView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss()
                    return true
                }
                if (selectedPosition == null) {
                    return false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedPosition = (selectedPosition - 1).coerceAtLeast(0)
                        listView.setSelection(selectedPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedPosition = (selectedPosition + 1).coerceAtMost(playManager.channels.size - 1)
                        listView.setSelection(selectedPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        playManager.play(selectedPosition)
                        dismiss()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
}
