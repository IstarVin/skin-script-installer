package com.istarvin.skinscriptinstaller.data.network.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse = execute(
        Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .get()
            .build(),
    )

    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json; charset=utf-8",
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse = execute(
        Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .post(body.toRequestBody(contentType.toMediaType()))
            .build(),
    )

    private suspend fun execute(request: Request): HttpResponse = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            HttpResponse(
                code = response.code,
                message = response.message,
                body = response.body.string(),
                contentType = response.body.contentType()?.toString(),
                headers = response.headers.toMap(),
                url = response.request.url.toString(),
            )
        }
    }
}

data class HttpResponse(
    val code: Int,
    val message: String,
    val body: String,
    val contentType: String?,
    val headers: Map<String, String>,
    val url: String,
) {
    val isSuccessful: Boolean = code in 200..299

    val isHtml: Boolean = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.equals("text/html", ignoreCase = true) == true

    fun parseHtmlOrNull(): Document? {
        if (!isHtml) return null
        return Jsoup.parse(body, url)
    }

    fun requireHtml(): Document {
        return parseHtmlOrNull() ?: error("Response is not HTML. Content-Type: $contentType")
    }
}

private fun Map<String, String>.toOkHttpHeaders(): Headers {
    return Headers.Builder().apply {
        forEach { (name, value) -> add(name, value) }
    }.build()
}
