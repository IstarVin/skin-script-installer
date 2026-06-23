package com.istarvin.skinscriptinstaller.data.downlink

import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownlinkRepositoryDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: DownlinkScriptParser,
    private val cache: DownlinkRepositoryCache
) {
    suspend fun fetchScripts(
        heroes: List<Hero>,
        forceRefresh: Boolean = false
    ): Result<List<DownlinkRepositoryEntry>> =
        withContext(Dispatchers.IO) {
            val cachedEntries = cache.read()
            if (!forceRefresh && cachedEntries.isNotEmpty()) {
                return@withContext Result.success(cachedEntries)
            }

            runCatching {
                val request = Request.Builder()
                    .url(DOWNLINK_URL)
                    .get()
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Downlink fetch failed: HTTP ${response.code}")
                    }
                    val html = response.body.string()
                    parser.parse(html, heroes).also(cache::write)
                }
            }
        }

    companion object {
        private const val DOWNLINK_URL = "https://downlink.my.id/"
    }
}
