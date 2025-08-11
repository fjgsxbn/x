import android.content.Context
import com.squareup.okhttp3.*
import com.taoweiji.quickjs.JSFunction
import java.io.IOException
import java.nio.charset.StandardCharsets

class FetchBridge(private val context: Context) {
    // OkHttp 客户端（全局单例）
    private val okHttpClient by lazy { OkHttpClient() }

    /**
     * 暴露给 JS 的 fetch 底层实现
     * @param url 请求地址
     * @param method 请求方法（GET/POST 等）
     * @param headers 请求头（JSON 字符串，如 {"Content-Type": "application/json"}）
     * @param body 请求体（字符串）
     * @param successCallback JS 成功回调函数
     * @param errorCallback JS 失败回调函数
     */
    fun fetch(
        url: String,
        method: String,
        headers: String?,
        body: String?,
        successCallback: JSFunction,
        errorCallback: JSFunction
    ) {
        // 1. 构建请求体
        val requestBody = body?.let {
            RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                it.toByteArray(StandardCharsets.UTF_8)
            )
        }

        // 2. 构建请求
        val requestBuilder = Request.Builder().url(url).method(method, requestBody)
        // 添加请求头（解析 JSON 字符串）
        headers?.takeIf { it.isNotBlank() }?.let {
            try {
                // 简易解析 JSON 头（实际项目可使用 Gson 等库）
                val headerMap = parseHeaders(it)
                headerMap.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
            } catch (e: Exception) {
                errorCallback.call("解析请求头失败：${e.message}")
                return
            }
        }

        // 3. 发送网络请求
        okHttpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 失败回调（切换到 JS 线程执行）
                successCallback.context.post {
                    errorCallback.call("网络请求失败：${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    // 读取响应体
                    val responseBody = response.body?.string() ?: ""
                    // 构建响应对象（JS 侧需要的结构）
                    val responseJson = """
                        {
                            "status": ${response.code()},
                            "statusText": "${response.message()}",
                            "body": $responseBody,
                            "headers": ${parseResponseHeaders(response.headers())}
                        }
                    """.trimIndent()
                    // 成功回调（切换到 JS 线程执行）
                    successCallback.context.post {
                        successCallback.call(responseJson)
                    }
                } catch (e: Exception) {
                    successCallback.context.post {
                        errorCallback.call("处理响应失败：${e.message}")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    /**
     * 简易解析请求头 JSON（实际项目建议用 Gson/Jackson）
     */
    private fun parseHeaders(headersJson: String): Map<String, String> {
        // 示例：假设输入格式为 {"key1":"value1","key2":"value2"}
        val map = mutableMapOf<String, String>()
        if (headersJson.startsWith("{") && headersJson.endsWith("}")) {
            val content = headersJson.substring(1, headersJson.length - 1)
            content.split(",").forEach { pair ->
                val keyValue = pair.split(":", limit = 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim().replace("\"", "")
                    val value = keyValue[1].trim().replace("\"", "")
                    map[key] = value
                }
            }
        }
        return map
    }

    /**
     * 解析响应头为 JSON 字符串
     */
    private fun parseResponseHeaders(headers: Headers): String {
        val headerMap = mutableMapOf<String, String>()
        for (i in 0 until headers.size()) {
            headerMap[headers.name(i)] = headers.value(i)
        }
        // 简易转 JSON（实际项目建议用 Gson）
        return headerMap.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
            "\"${it.key}\":\"${it.value}\""
        }
    }
}
