package de.schnubbirunkel.barkeep.web

import kotlin.js.Promise

/*
 * Small JavaScript helpers (fetch, storage, files, sharing). Kept as plain JS so the
 * Kotlin side only passes strings and numbers across the boundary.
 */

internal fun jsNow(): Double = js("Date.now()")

internal fun jsNavigatorLanguage(): String = js("(navigator.language || 'en')")

// ---------- HTTP ----------
internal fun jsFetch(method: String, url: String, body: String?, headersJson: String, timeoutMs: Int): Promise<JsAny?> = js("""
    fetch(url, {
        method: method,
        body: body == null ? undefined : body,
        headers: JSON.parse(headersJson),
        signal: (typeof AbortSignal !== 'undefined' && AbortSignal.timeout) ? AbortSignal.timeout(timeoutMs) : undefined
    }).then(async (r) => ({ status: r.status, text: await r.text() }))
""")

internal fun jsResponseStatus(r: JsAny?): Int = js("r.status")
internal fun jsResponseText(r: JsAny?): String = js("r.text")

/** Fetches a binary file and resolves to its content as base64. */
internal fun jsFetchBase64(url: String): Promise<JsAny?> = js("""
    fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.blob(); })
        .then(b => new Promise((ok, fail) => { const f = new FileReader(); f.onload = () => ok(String(f.result).split(',')[1]); f.onerror = fail; f.readAsDataURL(b); }))
""")

// ---------- localStorage ----------
internal fun jsLocalGet(key: String): String? = js("(() => { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
internal fun jsLocalSet(key: String, value: String): Boolean = js("(() => { try { localStorage.setItem(key, value); return true; } catch (e) { return false; } })()")

// ---------- IndexedDB (large data: downloaded recipe collections) ----------
internal fun jsIdbGet(key: String): Promise<JsAny?> = js("window.barkeepIdb.get(key)")
internal fun jsIdbPut(key: String, value: String): Promise<JsAny?> = js("window.barkeepIdb.put(key, value)")
internal fun jsIdbDelete(key: String): Promise<JsAny?> = js("window.barkeepIdb.del(key)")
internal fun jsRequestPersistence() { js("try { navigator.storage && navigator.storage.persist && navigator.storage.persist(); } catch (e) {}") }

// ---------- files, sharing, clipboard ----------
internal fun jsShareText(text: String): Promise<JsAny?> = js("""
    (navigator.share ? navigator.share({ text: text }) : navigator.clipboard.writeText(text)).then(() => true).catch(() => false)
""")
internal fun jsDownload(name: String, mime: String, text: String) { js("""
    (() => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([text], { type: mime }));
        a.download = name;
        document.body.appendChild(a); a.click();
        setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    })()
""") }
internal fun jsPickTextFile(accept: String): Promise<JsAny?> = js("window.barkeepPickTextFile(accept)")
/** Resolves to { jpeg: base64 (≤1600px), small: base64 (≤1024px) } or null. */
internal fun jsPickPhoto(camera: Boolean): Promise<JsAny?> = js("window.barkeepPickPhoto(camera)")
internal fun jsPhotoLarge(p: JsAny?): String = js("p.jpeg")
internal fun jsPhotoSmall(p: JsAny?): String = js("p.small")
internal fun jsString(s: JsAny?): String? = js("(s == null ? null : String(s))")

// ---------- barcode ----------
/** Resolves to the scanned code, "" when cancelled, or rejects when no camera is available. */
internal fun jsScanBarcode(): Promise<JsAny?> = js("window.barkeepScanBarcode()")
