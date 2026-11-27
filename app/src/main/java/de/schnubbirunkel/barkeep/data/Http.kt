package de.schnubbirunkel.barkeep.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client. Platform-specific: the web build has its own Http.kt using fetch().
 * Everything that talks to the network goes through here so data and AI code stays shared.
 */
object Http {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): HttpResponse =
        request("GET", url, null, headers, timeoutMs)

    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 60000): HttpResponse =
        request("POST", url, body, mapOf("Content-Type" to "application/json") + headers, timeoutMs)

    private suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>, timeoutMs: Int): HttpResponse =
        withContext(Dispatchers.IO) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = minOf(timeoutMs, 15000)
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Barkeep-Android - https://github.com/Schnubbirunkel/Barkeep")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) doOutput = true
            }
            try {
                if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                c.disconnect()
            }
        }
}
