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
    val id: String,
    val title: String,
    val path: String,
    val timestamp: Long,
    val platform: String = "",
    val fileSizeBytes: Long = 0L,
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
                        id = obj.optString("id", obj.getLong("timestamp").toString()),
                        title = obj.getString("title"),
                        path = obj.getString("path"),
                        timestamp = obj.getLong("timestamp"),
                        platform = obj.optString("platform", ""),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                    )
                }.reversed()
            }.getOrDefault(emptyList())
        }

    suspend fun append(context: Context, record: DownloadRecord) {
        context.dataStore.edit { prefs ->
            val existing = runCatching { JSONArray(prefs[KEY_HISTORY] ?: "[]") }.getOrDefault(JSONArray())
            val obj = JSONObject().apply {
                put("id", record.id)
                put("title", record.title)
                put("path", record.path)
                put("timestamp", record.timestamp)
                put("platform", record.platform)
                put("fileSizeBytes", record.fileSizeBytes)
            }
            existing.put(obj)
            val trimmed = JSONArray()
            val start = maxOf(0, existing.length() - 50)
            for (i in start until existing.length()) trimmed.put(existing.get(i))
            prefs[KEY_HISTORY] = trimmed.toString()
        }
    }

    suspend fun delete(context: Context, id: String) {
        context.dataStore.edit { prefs ->
            val existing = runCatching { JSONArray(prefs[KEY_HISTORY] ?: "[]") }.getOrDefault(JSONArray())
            val updated = JSONArray()
            for (i in 0 until existing.length()) {
                val obj = existing.getJSONObject(i)
                val objId = obj.optString("id", obj.optLong("timestamp", 0L).toString())
                if (objId != id) updated.put(obj)
            }
            prefs[KEY_HISTORY] = updated.toString()
        }
    }
}
