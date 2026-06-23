package com.istarvin.skinscriptinstaller.data.downlink

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownlinkRepositoryCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): List<DownlinkRepositoryEntry> {
        val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                array.getJSONObject(index).toEntry()
            }
        }.getOrDefault(emptyList())
    }

    fun write(entries: List<DownlinkRepositoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry -> array.put(entry.toJson()) }
        prefs.edit { putString(KEY_ENTRIES, array.toString()) }
    }

    private fun JSONObject.toEntry(): DownlinkRepositoryEntry = DownlinkRepositoryEntry(
        id = getString("id"),
        title = getString("title"),
        imageUrl = optStringOrNull("imageUrl"),
        heroName = getString("heroName"),
        heroIcon = optStringOrNull("heroIcon"),
        replacementSkinName = getString("replacementSkinName"),
        originalSkinName = getString("originalSkinName"),
        sfileUrl = getString("sfileUrl")
    )

    private fun DownlinkRepositoryEntry.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("imageUrl", imageUrl)
        .put("heroName", heroName)
        .put("heroIcon", heroIcon)
        .put("replacementSkinName", replacementSkinName)
        .put("originalSkinName", originalSkinName)
        .put("sfileUrl", sfileUrl)

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)

    private companion object {
        const val PREFS_NAME = "downlink_repository_cache"
        const val KEY_ENTRIES = "entries"
    }
}
