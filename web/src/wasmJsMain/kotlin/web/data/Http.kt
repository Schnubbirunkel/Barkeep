package de.schnubbirunkel.barkeep.data

import de.schnubbirunkel.barkeep.web.jsFetch
import de.schnubbirunkel.barkeep.web.jsResponseStatus
import de.schnubbirunkel.barkeep.web.jsResponseText
import kotlinx.coroutines.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** HTTP via the browser's fetch() (web counterpart of app/…/data/Http.kt). */
object Http {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): HttpResponse =
        request("GET", url, null, headers, timeoutMs)

    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 60000): HttpResponse =
        request("POST", url, body, mapOf("Content-Type" to "application/json") + headers, timeoutMs)

    private suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>, timeoutMs: Int): HttpResponse {
        val h = JsonObject(headers.mapValues { JsonPrimitive(it.value) }).toString()
        val r = try {
            jsFetch(method, url, body, h, timeoutMs).await<JsAny?>()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Network error (${url.substringAfter("://").substringBefore('/')})")
        }
        return HttpResponse(jsResponseStatus(r), jsResponseText(r))
    }
}
