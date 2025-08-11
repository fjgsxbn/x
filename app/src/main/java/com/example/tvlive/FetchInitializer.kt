package com.example.tvlive

import com.taoweiji.quickjs.JSContext
import com.taoweiji.quickjs.QuickJS

class FetchInitializer(private val context: Context) {
    fun init(jsContext: JSContext) {
        // 1. 绑定 Kotlin 桥接到 JS 全局对象
        jsContext.addJavaMethod(FetchBridge(context), "fetchBridge")

        // 2. 在 JS 中定义 fetch 方法（模拟浏览器 fetch API）
        val fetchJs = """
            // 全局 fetch 方法
            function fetch(url, options = {}) {
                // 默认参数处理
                const method = (options.method || 'GET').toUpperCase();
                const headers = options.headers || {};
                const body = options.body || null;
                
                // 转换 headers 为 JSON 字符串（传给 Kotlin 层）
                const headersJson = JSON.stringify(headers);
                
                // 返回 Promise
                return new Promise((resolve, reject) => {
                    // 调用 Kotlin 桥接的 fetch 实现
                    fetchBridge.fetch(
                        url,
                        method,
                        headersJson,
                        body,
                        // 成功回调（接收 Kotlin 返回的响应 JSON）
                        (responseJson) => {
                            const response = JSON.parse(responseJson);
                            // 封装响应对象（模拟浏览器 Response 接口）
                            resolve({
                                status: response.status,
                                statusText: response.statusText,
                                headers: response.headers,
                                text: () => Promise.resolve(response.body),
                                json: () => Promise.resolve(JSON.parse(response.body))
                            });
                        },
                        // 失败回调
                        (errorMessage) => {
                            reject(new Error(errorMessage));
                        }
                    );
                });
            }
            // 挂载到全局
            global.fetch = fetch;
        """.trimIndent()

        // 执行 JS 代码，初始化 fetch 方法
        jsContext.evaluate(fetchJs, "fetch.js")
    }
}
