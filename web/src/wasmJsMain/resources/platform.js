// Browser helpers used by the Kotlin/Wasm app (see web/src/wasmJsMain/kotlin/web/Js.kt).
(function () {
  'use strict';

  // ---------------------------------------------------------------- IndexedDB key/value store
  let dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise((resolve, reject) => {
        const req = indexedDB.open('barkeep', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('kv');
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    }
    return dbPromise;
  }
  function tx(mode, fn) {
    return db().then((d) => new Promise((resolve, reject) => {
      const t = d.transaction('kv', mode);
      const r = fn(t.objectStore('kv'));
      t.oncomplete = () => resolve(r && 'result' in r ? r.result : null);
      t.onerror = () => reject(t.error);
    }));
  }
  window.barkeepIdb = {
    get: (k) => tx('readonly', (s) => s.get(k)).then((v) => (v == null ? null : String(v))).catch(() => null),
    put: (k, v) => tx('readwrite', (s) => s.put(v, k)).then(() => true).catch(() => false),
    del: (k) => tx('readwrite', (s) => s.delete(k)).then(() => true).catch(() => false),
  };

  // ---------------------------------------------------------------- file pickers
  function pickFile(accept, capture) {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = accept;
      if (capture) input.setAttribute('capture', 'environment');
      input.style.display = 'none';
      let done = false;
      const finish = (f) => { if (!done) { done = true; input.remove(); resolve(f); } };
      input.onchange = () => finish(input.files && input.files[0] ? input.files[0] : null);
      input.addEventListener('cancel', () => finish(null));
      document.body.appendChild(input);
      input.click();
    });
  }

  window.barkeepPickTextFile = (accept) =>
    pickFile(accept || '*/*', false).then((f) => (f ? f.text() : null));

  function loadImage(file) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
      img.onerror = (e) => { URL.revokeObjectURL(url); reject(e); };
      img.src = url;
    });
  }
  function jpeg(img, maxSide) {
    const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
    const c = document.createElement('canvas');
    c.width = Math.round(img.naturalWidth * scale);
    c.height = Math.round(img.naturalHeight * scale);
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', 0.82).split(',')[1];
  }
  /** Resolves to { jpeg, small } (base64 JPEGs, ≤1600 px for display and ≤1024 px for the AI) or null. */
  window.barkeepPickPhoto = (camera) =>
    pickFile('image/*', camera).then((f) => {
      if (!f) return null;
      return loadImage(f).then((img) => ({ jpeg: jpeg(img, 1600), small: jpeg(img, 1024) }));
    }).catch(() => null);

  // ---------------------------------------------------------------- barcode scanner overlay
  let zxingPromise = null;
  function loadZxing() {
    if (window.ZXing) return Promise.resolve(window.ZXing);
    if (!zxingPromise) {
      zxingPromise = new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = 'vendor/zxing.min.js';
        s.onload = () => resolve(window.ZXing);
        s.onerror = () => { zxingPromise = null; reject(new Error('scanner')); };
        document.head.appendChild(s);
      });
    }
    return zxingPromise;
  }

  /** Opens a full-screen camera view. Resolves to the code, "" when cancelled; rejects without camera. */
  window.barkeepScanBarcode = () => {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return Promise.reject(new Error('no camera'));
    return loadZxing().then((ZXing) => new Promise((resolve, reject) => {
      const overlay = document.createElement('div');
      overlay.className = 'bk-scan';
      overlay.innerHTML =
        '<video playsinline muted></video><div class="bk-scan-frame"></div>' +
        '<button type="button" class="bk-scan-close" aria-label="Close">✕</button>';
      document.body.appendChild(overlay);
      const video = overlay.querySelector('video');
      const hints = new Map();
      hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, [
        ZXing.BarcodeFormat.EAN_13, ZXing.BarcodeFormat.EAN_8, ZXing.BarcodeFormat.UPC_A, ZXing.BarcodeFormat.UPC_E,
      ]);
      hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
      const reader = new ZXing.BrowserMultiFormatReader(hints, 250);
      let finished = false;
      const close = (fn) => {
        if (finished) return;
        finished = true;
        try { reader.reset(); } catch (e) { /* ignore */ }
        overlay.remove();
        fn();
      };
      overlay.querySelector('.bk-scan-close').onclick = () => close(() => resolve(''));
      reader.decodeFromConstraints({ video: { facingMode: 'environment' } }, video, (result) => {
        if (result) close(() => resolve(result.getText()));
      }).catch((e) => close(() => reject(e)));
    }));
  };
})();
