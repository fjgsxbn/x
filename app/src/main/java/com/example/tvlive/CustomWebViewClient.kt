import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CustomWebViewClient : WebViewClient() {
    // 初始化 OkHttp 客户端
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // 只处理 HTTP/HTTPS 请求
        val scheme = request.url.scheme
        if (scheme == null || !scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        return try {
            // 1. 解析请求参数
            val url = request.url.toString()
            val method = request.method
            val headers = request.requestHeaders
            val bodyInputStream = request.body

            // 2. 构建 OkHttp 请求
            val requestBuilder = Request.Builder().url(url)

            // 3. 添加请求头
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 4. 处理请求体（非 GET 方法）
            if (method != "GET" && bodyInputStream != null) {
                val bodyBytes = inputStreamToBytes(bodyInputStream)
                val mediaType = getMediaTypeFromHeaders(headers)
                val requestBody = RequestBody.create(mediaType, bodyBytes)
                requestBuilder.method(method, requestBody)
            }

            // 5. 发起原生请求
            val okResponse = okHttpClient.newCall(requestBuilder.build()).execute()

            // 6. 封装响应并返回给 WebView
            WebResourceResponse(
                okResponse.body?.contentType()?.toString(),
                okResponse.header("Content-Encoding", "UTF-8"),
                okResponse.body?.byteStream()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null // 异常时返回 null，让 WebView 自行处理
        }
    }

    // 工具方法：InputStream 转字节数组（读取请求体）
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

    // 工具方法：从请求头获取 Content-Type
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                return MediaType.parse(value)
            }
        }
        return MediaType.parse("application/octet-stream") // 默认类型
    }
}
