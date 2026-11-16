const CACHE_NAME = "notifaula-pwa-v8";
const ASSETS = [
  "./pwa.html",
  "./manifest.json",
  "./icon.svg",
  "./icon-192.png",
  "./icon-512.png",
  "./apple-touch-icon.png",
  "./NotifAula.js"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});

self.addEventListener("push", (event) => {
  let data = {
    title: "NotifAula",
    body: "Nueva novedad en el Aula Virtual de la UAL",
    url: "https://aulavirtual.ual.es/ultra/stream"
  };
  if (event.data) {
    try {
      data = Object.assign(data, event.data.json());
    } catch (e) {
      data.body = event.data.text();
    }
  }
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: "./icon-192.png",
      badge: "./icon-192.png",
      data: { url: data.url || "https://aulavirtual.ual.es/ultra/stream" }
    })
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || "https://aulavirtual.ual.es/ultra/stream";
  event.waitUntil(
    self.clients.openWindow(targetUrl)
  );
});
