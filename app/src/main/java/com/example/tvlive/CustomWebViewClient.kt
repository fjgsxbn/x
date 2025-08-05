package com.example.tvlive // 必须放在文件第一行，无其他内容

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull // 关键：显式导入扩展函数
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CustomWebViewClient : WebViewClient() {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION") // 允许低版本API使用
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val scheme = request.url.scheme
        if (scheme == null || !scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        return try {
            val url = request.url.toString()
            val method = request.method
            val headers = request.requestHeaders

            // 修复：严格处理 request.body 的 API 兼容性
            var bodyInputStream: InputStream? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 仅 API 21+ 支持 request.body，低版本直接忽略
                bodyInputStream = request.body
            }

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 处理非 GET 请求的 body（仅当 body 存在时）
            if (method != "GET" && bodyInputStream != null) {
                val bodyBytes = inputStreamToBytes(bodyInputStream)
                val mediaType = getMediaTypeFromHeaders(headers)
                val requestBody = mediaType?.let { RequestBody.create(it, bodyBytes) }
                requestBuilder.method(method, requestBody)
            }

            // 发起请求并返回响应
            val okResponse: Response = okHttpClient.newCall(requestBuilder.build()).execute()
            WebResourceResponse(
                okResponse.body?.contentType()?.toString(),
                okResponse.header("Content-Encoding", "UTF-8"),
                okResponse.body?.byteStream(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 工具方法：InputStream 转字节数组
    private fun inputStreamToBytes(inputStream: InputStream): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.flush()
        return outputStream.toByteArray()
    }

    // 修复：获取 MediaType（适配 OkHttp 4+）
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                return value.toMediaTypeOrNull() // 使用扩展函数
            }
        }
        return "application/octet-stream".toMediaTypeOrNull() // 默认类型
    }
}
