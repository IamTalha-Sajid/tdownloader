package com.tdownload.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "download_history")

data class DownloadRecord(
    val title: String,
    val path: String,
    val timestamp: Long,
)

object DownloadStore {
    private val KEY_HISTORY = stringPreferencesKey("history")

    fun historyFlow(context: Context): Flow<List<DownloadRecord>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_HISTORY] ?: return@map emptyList()
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    DownloadRecord(
                        title = obj.getString("title"),
                        path = obj.getString("path"),
                        timestamp = obj.getLong("timestamp"),
                    )
                }.reversed()
            }.getOrDefault(emptyList())
        }

    suspend fun append(context: Context, record: DownloadRecord) {
        context.dataStore.edit { prefs ->
            val existing = runCatching { JSONArray(prefs[KEY_HISTORY] ?: "[]") }.getOrDefault(JSONArray())
            val obj = JSONObject().apply {
                put("title", record.title)
                put("path", record.path)
                put("timestamp", record.timestamp)
            }
            existing.put(obj)
            // keep last 50
            val trimmed = JSONArray()
            val start = maxOf(0, existing.length() - 50)
            for (i in start until existing.length()) trimmed.put(existing.get(i))
            prefs[KEY_HISTORY] = trimmed.toString()
        }
    }
}
