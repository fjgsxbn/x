package com.example.tvlive // 包名必须在第一行

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.MediaType
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

            // 修复：处理 request.body 的 API 兼容性（仅 API 21+ 可用）
            var bodyInputStream: InputStream? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 明确 API 21+ 才支持 body，低版本为 null
                bodyInputStream = request.body
            }

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 修复：处理非 GET 请求的 body（修正 lambda 变量引用）
            if (method != "GET" && bodyInputStream != null) {
                val bodyBytes = inputStreamToBytes(bodyInputStream)
                val mediaType = getMediaTypeFromHeaders(headers)
                // 显式声明变量名，避免 "it" 引用错误
                val requestBody = mediaType?.let { mediaType ->
                    RequestBody.create(mediaType, bodyBytes)
                }
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

    // 修复：获取 MediaType（确保导入 okhttp3.MediaType）
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                // 根据 OkHttp 版本选择：
                // 若为 OkHttp 3.x，用 MediaType.parse(value)
                // 若为 OkHttp 4.x，用 value.toMediaTypeOrNull()
                return MediaType.parse(value) // 先适配 3.x，若版本为 4+ 请替换为下面一行
                // return value.toMediaTypeOrNull()
            }
        }
        // 默认类型
        return MediaType.parse("application/octet-stream") // 同上，4+ 用 "application/octet-stream".toMediaTypeOrNull()
    }
}
