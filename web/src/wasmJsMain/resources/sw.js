// Barkeep service worker: makes the app work offline after the first visit.
// - app files: network first (so updates arrive), cached copy when offline
// - hashed .wasm files, fonts, icons, vendor scripts: cache first (they never change under the same name)
// - drink photos from TheCocktailDB / Open Food Facts: cache first, so favourites keep their pictures offline
const APP = 'barkeep-app-@BUILD@';
const IMG = 'barkeep-img-v1';
const SHELL = ['./', 'index.html', 'barkeep.js', 'platform.js', 'manifest.webmanifest', 'recipes.json', 'ingredients.json',
  'fonts/inter.ttf', 'fonts/plus_jakarta_sans.ttf', 'icons/icon-192.png', 'icons/icon.svg'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(APP).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== APP && k !== IMG).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

function cacheFirst(req, cacheName) {
  return caches.open(cacheName).then((c) => c.match(req).then((hit) => hit || fetch(req).then((res) => {
    if (res.ok || res.type === 'opaque') c.put(req, res.clone());
    return res;
  })));
}

function networkFirst(req) {
  return fetch(req).then((res) => {
    if (res.ok) caches.open(APP).then((c) => c.put(req, res.clone()));
    return res;
  }).catch(() => caches.match(req, { ignoreSearch: true }).then((hit) => hit || caches.match('index.html')));
}

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin === self.location.origin) {
    if (/\.(wasm|ttf|png|svg)$/.test(url.pathname) || url.pathname.includes('/vendor/')) e.respondWith(cacheFirst(req, APP));
    else e.respondWith(networkFirst(req));
  } else if (/\.(jpe?g|png|webp)$/i.test(url.pathname)) {
    e.respondWith(cacheFirst(req, IMG));
  }
});
