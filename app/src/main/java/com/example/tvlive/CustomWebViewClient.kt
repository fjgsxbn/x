package com.example.tvlive // 包名必须放在文件第一行

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

            // 处理请求体（严格API版本判断）
            var bodyInputStream: InputStream? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 仅API 21+支持request.body
                bodyInputStream = request.body
            }

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 处理非GET请求的body
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

    // 适配OkHttp 4+的MediaType处理
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                return value.toMediaTypeOrNull() // 仅OkHttp 4+支持
            }
        }
        return "application/octet-stream".toMediaTypeOrNull()
    }
}
