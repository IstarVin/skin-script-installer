package com.istarvin.skinscriptinstaller.data.downlink

import com.istarvin.skinscriptinstaller.data.network.http.HttpClient
import com.istarvin.skinscriptinstaller.data.network.http.HttpResponse
import org.jsoup.nodes.Document
import javax.inject.Inject

interface SFileDirectDownloadResolver {
    suspend fun resolve(sfileUrl: String): Result<String>
}

class SFileDirectDownloadResolverImpl @Inject constructor(
    private val httpClient: HttpClient
) : SFileDirectDownloadResolver {
    override suspend fun resolve(sfileUrl: String): Result<String> = runCatching {
        val landingResponse = httpClient.get(sfileUrl)
        val intermediateUrl = landingResponse.requireSuccessfulHtml().extractIntermediateDownloadUrl()
        val intermediatePage = httpClient.get(
            url = intermediateUrl,
            headers = landingResponse.cookieHeaderForNextRequest()
        ).requireSuccessfulBody()

        intermediatePage.extractDirectDownloadUrl()
    }

    private fun HttpResponse.requireSuccessfulHtml(): Document {
        require(isSuccessful) { "SFile page request failed: HTTP $code" }
        return requireHtml()
    }

    private fun HttpResponse.requireSuccessfulBody(): String {
        require(isSuccessful) { "SFile download page request failed: HTTP $code" }
        return body
    }

    private fun HttpResponse.cookieHeaderForNextRequest(): Map<String, String> {
        val cookie = setCookieHeaders
            .mapNotNull { it.substringBefore(';').takeIf(String::isNotBlank) }
            .joinToString(separator = "; ")

        return if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie)
    }

    private fun Document.extractIntermediateDownloadUrl(): String {
        val downloadElement = selectFirst("#download[data-dw-url], [data-dw-url]")
            ?: error("SFile page does not contain a download token")
        val url = downloadElement.absUrl("data-dw-url").ifBlank {
            downloadElement.attr("data-dw-url")
        }

        return url.ifBlank { error("SFile download token is empty") }
    }

    private fun String.extractDirectDownloadUrl(): String {
        return DIRECT_DOWNLOAD_REGEX.find(this)
            ?.groupValues
            ?.get(DIRECT_DOWNLOAD_URL_GROUP)
            ?.takeIf { it.isNotBlank() }
            ?: error("SFile direct download URL was not found")
    }

    companion object {
        private const val DIRECT_DOWNLOAD_URL_GROUP = 2
        private val DIRECT_DOWNLOAD_REGEX = Regex(
            pattern = """downloadButton\.href\s*=\s*(['"])(.*?)\1""",
            option = RegexOption.DOT_MATCHES_ALL
        )
    }
}
