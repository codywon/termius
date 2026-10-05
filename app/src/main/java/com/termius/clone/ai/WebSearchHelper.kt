package com.termius.clone.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class WebSearchItem(
    val title: String,
    val snippet: String,
    val url: String
)

data class WebSearchResult(
    val query: String,
    val count: Int,
    val items: List<WebSearchItem>,
    val source: String,
    val error: String? = null
)

/**
 * 生产级双通道轻量联网检索引擎：
 * 1. 专为运维工程师排查故障打造，免去第三方昂贵搜索 API Key；
 * 2. 双轨搜索引擎自愈架构：默认直连必应 (Bing CN)，自动回退 DuckDuckGo HTML；
 * 3. 毫秒级提取 Linux 错误代码、Docker 容器故障、Nginx 配置语法与排障手册；
 * 4. 纯原生 HttpURLConnection + 正则流式提取，零额外第三方依赖。
 */
object WebSearchHelper {

    private const val TAG = "WebSearchHelper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun search(query: String, maxResults: Int = 4): WebSearchResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext WebSearchResult(trimmedQuery, 0, emptyList(), "None", "搜索关键词不能为空")
        }

        // 1. 优先尝试微软必应 (国内直连极速，中文工控与技术资料覆盖全面)
        try {
            val bingResult = searchBingCn(trimmedQuery, maxResults)
            if (bingResult.items.isNotEmpty()) {
                return@withContext bingResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bing 搜索失败，尝试回退备用搜索引擎: ${e.message}")
        }

        // 2. 备用通道：DuckDuckGo HTML 版 (通用/海外技术文档与开源规约)
        try {
            val ddgResult = searchDuckDuckGo(trimmedQuery, maxResults)
            if (ddgResult.items.isNotEmpty()) {
                return@withContext ddgResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo 搜索失败: ${e.message}")
        }

        WebSearchResult(
            query = trimmedQuery,
            count = 0,
            items = emptyList(),
            source = "None",
            error = "未能获取到搜索结果，网络可能不稳定或未找到匹配资料"
        )
    }

    private fun searchBingCn(query: String, maxResults: Int): WebSearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://cn.bing.com/search?q=$encoded"
        val html = fetchHtml(urlStr, mapOf("Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"))

        val items = mutableListOf<WebSearchItem>()
        val algoPattern = Pattern.compile("<li class=\"b_algo\"[\\s\\S]*?</li>", Pattern.CASE_INSENSITIVE)
        val matcher = algoPattern.matcher(html)

        val titleUrlPattern = Pattern.compile("<h2><a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a></h2>", Pattern.CASE_INSENSITIVE)
        val snippetPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE)

        while (matcher.find() && items.size < maxResults) {
            val block = matcher.group()
            val tuMatcher = titleUrlPattern.matcher(block)
            if (tuMatcher.find()) {
                val rawUrl = tuMatcher.group(1) ?: ""
                val rawTitle = tuMatcher.group(2) ?: ""

                var rawSnippet = ""
                val snipMatcher = snippetPattern.matcher(block)
                if (snipMatcher.find()) {
                    rawSnippet = snipMatcher.group(1) ?: ""
                }

                val cleanTitle = cleanHtmlTags(rawTitle)
                val cleanSnippet = cleanHtmlTags(rawSnippet)

                if (cleanTitle.isNotBlank() && rawUrl.startsWith("http")) {
                    items.add(
                        WebSearchItem(
                            title = cleanTitle,
                            snippet = cleanSnippet,
                            url = rawUrl
                        )
                    )
                }
            }
        }

        return WebSearchResult(
            query = query,
            count = items.size,
            items = items,
            source = "Bing CN"
        )
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): WebSearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetchHtml(urlStr, emptyMap())

        val items = mutableListOf<WebSearchItem>()
        val resultPattern = Pattern.compile("<div class=\"result__body\">[\\s\\S]*?</div>\\s*</div>", Pattern.CASE_INSENSITIVE)
        val matcher = resultPattern.matcher(html)

        val titleUrlPattern = Pattern.compile("<a class=\"result__snippet\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)
        val plainTitlePattern = Pattern.compile("<a class=\"result__url\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)
        val snippetPattern = Pattern.compile("<a class=\"result__snippet\"[\\s\\S]*?>(.*?)</a>", Pattern.CASE_INSENSITIVE)

        while (matcher.find() && items.size < maxResults) {
            val block = matcher.group()
            var rawUrl = ""
            var rawTitle = ""
            var rawSnippet = ""

            val tuMatcher = titleUrlPattern.matcher(block)
            if (tuMatcher.find()) {
                rawUrl = tuMatcher.group(1) ?: ""
                rawSnippet = tuMatcher.group(2) ?: ""
            }

            val pMatcher = plainTitlePattern.matcher(block)
            if (pMatcher.find()) {
                if (rawUrl.isEmpty()) rawUrl = pMatcher.group(1) ?: ""
                rawTitle = pMatcher.group(2) ?: ""
            }

            if (rawSnippet.isEmpty()) {
                val sMatcher = snippetPattern.matcher(block)
                if (sMatcher.find()) {
                    rawSnippet = sMatcher.group(1) ?: ""
                }
            }

            val cleanTitle = cleanHtmlTags(if (rawTitle.isNotBlank()) rawTitle else rawSnippet)
            val cleanSnippet = cleanHtmlTags(rawSnippet)

            if (cleanTitle.isNotBlank()) {
                items.add(
                    WebSearchItem(
                        title = cleanTitle,
                        snippet = cleanSnippet,
                        url = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"
                    )
                )
            }
        }

        return WebSearchResult(
            query = query,
            count = items.size,
            items = items,
            source = "DuckDuckGo"
        )
    }

    private fun fetchHtml(urlString: String, extraHeaders: Map<String, String>): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun cleanHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
