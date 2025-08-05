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
    // 初始化 OkHttp 客户端（适配 4+ 版本）
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
            // 解析请求基本信息
            val url = request.url.toString()
            val method = request.method
            val headers = request.requestHeaders

            // 处理请求体（兼容 API 21+，低版本无 body 支持）
            val bodyInputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request.body // API 21+ 才支持 getBody()
            } else {
                null // 低版本返回 null，不处理 body
            }

            // 构建 OkHttp 请求
            val requestBuilder = Request.Builder().url(url)

            // 添加请求头（原样传递）
            headers.forEach { (key, value) ->
                if (!value.isNullOrEmpty()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            // 处理非 GET 方法的请求体
            if (method != "GET" && bodyInputStream != null) {
                val bodyBytes = inputStreamToBytes(bodyInputStream)
                val mediaType = getMediaTypeFromHeaders(headers)
                // 构建请求体（兼容 OkHttp 4+）
                val requestBody = mediaType?.let { RequestBody.create(it, bodyBytes) }
                requestBuilder.method(method, requestBody)
            }

            // 发起网络请求
            val okResponse: Response = okHttpClient.newCall(requestBuilder.build()).execute()

            // 封装响应返回给 WebView
            WebResourceResponse(
                okResponse.body?.contentType()?.toString(), // 响应类型
                okResponse.header("Content-Encoding", "UTF-8"), // 编码
                okResponse.body?.byteStream() // 响应体
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null // 异常时让 WebView 自行处理
        }
    }

    // 工具方法：InputStream 转字节数组（读取请求体内容）
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

    // 工具方法：从请求头获取 Content-Type（适配 OkHttp 4+ 的扩展函数）
    private fun getMediaTypeFromHeaders(headers: Map<String, String>): MediaType? {
        headers.forEach { (key, value) ->
            if (key.equals("Content-Type", ignoreCase = true)) {
                return value.toMediaTypeOrNull() // 替代 MediaType.parse()
            }
        }
        // 默认类型（二进制流）
        return "application/octet-stream".toMediaTypeOrNull()
    }
}
