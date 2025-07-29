package com.example.tvlive

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import com.example.tvlive.model.Channel

class ChannelListDialog(
    context: Context,
    private val channels: List<Channel>,
    private val currentPosition: Int,
    private val onChannelSelected: (Int) -> Unit
) : Dialog(context, android.R.style.Theme_NoTitleBar_Fullscreen) {

    private lateinit var listView: ListView
    private var selectedPosition = currentPosition

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
            channels.map { it.name }
        )
        listView.adapter = adapter
        listView.setSelection(selectedPosition)
        listView.requestFocus()

        // 列表项点击事件
        listView.setOnItemClickListener { _, _, position, _ ->
            onChannelSelected(position)
            dismiss()
        }

        // 遥控器按键监听
        listView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedPosition = (selectedPosition - 1).coerceAtLeast(0)
                        listView.setSelection(selectedPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedPosition = (selectedPosition + 1).coerceAtMost(channels.size - 1)
                        listView.setSelection(selectedPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        onChannelSelected(selectedPosition)
                        dismiss()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        dismiss()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
}
