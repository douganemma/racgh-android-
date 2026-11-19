package com.racghana.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Native REST client for RaC Ghana. Requests run off the Android main thread. */
object RacApi {
    private const val BASE_URL = "https://racghana.com/api/"
    data class ApiResult(val status: Int, val body: String) {
        fun json(): JSONObject = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
    }
    suspend fun get(path: String, params: Map<String, String> = emptyMap(), bearerToken: String? = null): ApiResult =
        request("GET", path, params, null, bearerToken)
    suspend fun post(path: String, values: Map<String, String>, bearerToken: String? = null): ApiResult =
        request("POST", path, emptyMap(), JSONObject(values).toString(), bearerToken)

    private suspend fun request(method: String, path: String, params: Map<String, String>, payload: String?, bearerToken: String? = null): ApiResult =
        withContext(Dispatchers.IO) {
            val query = params.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            val address = BASE_URL + path.trimStart('/') + if (query.isNotEmpty()) "?$query" else ""
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "RaCGhana-Android/1.0")
                if (payload != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                if (!bearerToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearerToken")
                if (payload != null) connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..399) connection.inputStream else connection.errorStream
                ApiResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally { connection.disconnect() }
        }

    fun rows(result: ApiResult): List<JSONObject> {
        val root = result.json()
        val keys = listOf("data", "results", "cars", "services", "items", "rows")
        for (key in keys) {
            val value = root.opt(key)
            if (value is JSONArray) return (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            if (value is JSONObject) for (nested in keys) {
                val array = value.optJSONArray(nested)
                if (array != null) return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
        }
        if (root.has("id") || root.has("title") || root.has("name")) return listOf(root)
        return emptyList()
    }
    fun deepText(obj: JSONObject, vararg keys: String): String {
        val wanted = keys.toSet()
        fun scan(value: Any?): String {
            when (value) {
                is JSONObject -> {
                    for (key in wanted) {
                        val candidate = firstText(value, key)
                        if (candidate.isNotBlank()) return candidate
                    }
                    val iterator = value.keys()
                    while (iterator.hasNext()) {
                        val found = scan(value.opt(iterator.next()))
                        if (found.isNotBlank()) return found
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) {
                    val found = scan(value.opt(i))
                    if (found.isNotBlank()) return found
                }
            }
            return ""
        }
        return scan(obj)
    }

    fun errorMessage(obj: JSONObject): String {
        val direct = deepText(obj, "message", "error", "detail", "description")
        if (direct.isNotBlank()) return direct
        val errors = obj.optJSONObject("errors") ?: return ""
        val messages = mutableListOf<String>()
        val iterator = errors.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = errors.opt(key)
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) {
                    value.optString(i).takeIf { it.isNotBlank() }?.let { messages.add(it) }
                }
                else -> value?.toString()?.takeIf { it.isNotBlank() }?.let { messages.add(it) }
            }
        }
        return messages.distinct().joinToString(" ")
    }

    fun firstText(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.opt(key)
            if (value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) {
                val text = value.toString().trim()
                if (text.isNotEmpty() && text != "null") return text
            }
        }
        return ""
    }
    fun detailObject(result: ApiResult): JSONObject? {
        var current = result.json()
        repeat(5) {
            val direct = listOf("data", "result", "car", "service", "item", "detail").firstNotNullOfOrNull { key ->
                current.optJSONObject(key)
            }
            if (direct != null) current = direct else return current.takeIf {
                it.has("id") || it.has("title") || it.has("name") || it.has("description") || it.has("content")
            }
        }
        return current
    }

    fun unwrap(result: ApiResult): JSONObject {
        val root = result.json()
        return root.optJSONObject("data") ?: root.optJSONObject("result") ?: root
    }
}
