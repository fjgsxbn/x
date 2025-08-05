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

// 确保类名与文件名一致，且在正确的包名下
package com.example.tvlive

class CustomWebViewClient : WebViewClient() {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val scheme = request.url.scheme
        if (scheme == null || !scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        return try {
            val url = request.url.toString()
            val method = request.method
            val headers = request.requestHeaders

            // 修复：明确处理 API 版本，避免 body 引用错误
            var bodyInputStream: InputStream? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bodyInputStream = request.body // 仅 API 21+ 支持
            }

            val requestBuilder = Request.Builder().url(url)

            // 添加请求头
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 修复：检查 bodyInputStream 非空，避免智能转换错误
            if (method != "GET" && bodyInputStream != null) {
                val bodyBytes = inputStreamToBytes(bodyInputStream)
                val mediaType = getMediaTypeFromHeaders(headers)
                val requestBody = mediaType?.let { RequestBody.create(it, bodyBytes) }
                requestBuilder.method(method, requestBody)
            }

            // 发起请求
            val okResponse: Response = okHttpClient.newCall(requestBuilder.build()).execute()

            // 构建响应
            WebResourceResponse(
                okResponse.body?.contentType()?.toString(),
                okResponse.header("Content-Encoding", "UTF-8"),
                okResponse.body?.byteStream()
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

    // 修复：兼容 OkHttp 3.x 和 4.x 的 MediaType 处理
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                // 同时兼容 OkHttp 3.x（parse）和 4.x（toMediaTypeOrNull）
                return try {
                    // 尝试 OkHttp 4.x 方法
                    value.toMediaTypeOrNull()
                } catch (e: NoSuchMethodError) {
                    //  fallback 到 OkHttp 3.x 方法
                    MediaType.parse(value)
                }
            }
        }
        // 默认类型
        return try {
            "application/octet-stream".toMediaTypeOrNull()
        } catch (e: NoSuchMethodError) {
            MediaType.parse("application/octet-stream")
        }
    }
}
