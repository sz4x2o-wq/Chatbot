package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.bookmarkDataStore: DataStore<Preferences> by preferencesDataStore(name = "sarkari_bookmarks")

data class SavedBookmark(
    val url: String,
    val title: String,
    val savedAt: Long
)

class BookmarkRepository(private val context: Context) {

    private val bookmarksKey = stringPreferencesKey("saved_bookmarks_json")

    val bookmarksFlow: Flow<List<SavedBookmark>> = context.bookmarkDataStore.data.map { prefs ->
        parseBookmarks(prefs[bookmarksKey])
    }

    suspend fun toggleBookmark(url: String, title: String): Boolean {
        var added = false
        context.bookmarkDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[bookmarksKey]).toMutableList()
            val normalizedUrl = url.trim()
            val existingIndex = current.indexOfFirst { it.url == normalizedUrl }
            if (existingIndex >= 0) {
                current.removeAt(existingIndex)
                added = false
            } else {
                val cleanTitle = title.ifBlank { "Sarkari Sahayata" }.trim()
                current.add(
                    0,
                    SavedBookmark(
                        url = normalizedUrl,
                        title = cleanTitle,
                        savedAt = System.currentTimeMillis()
                    )
                )
                added = true
            }
            prefs[bookmarksKey] = serializeBookmarks(current)
        }
        return added
    }

    suspend fun removeBookmark(url: String) {
        context.bookmarkDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[bookmarksKey])
                .filterNot { it.url == url.trim() }
            prefs[bookmarksKey] = serializeBookmarks(current)
        }
    }

    suspend fun clearAll() {
        context.bookmarkDataStore.edit { prefs ->
            prefs.remove(bookmarksKey)
        }
    }

    private fun parseBookmarks(rawJson: String?): List<SavedBookmark> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    val title = obj.optString("title")
                    val savedAt = obj.optLong("savedAt", 0L)
                    if (url.isNotBlank()) {
                        add(SavedBookmark(url = url, title = title, savedAt = savedAt))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeBookmarks(bookmarks: List<SavedBookmark>): String {
        val array = JSONArray()
        bookmarks.forEach { item ->
            val obj = JSONObject().apply {
                put("url", item.url)
                put("title", item.title)
                put("savedAt", item.savedAt)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
