/**
 * @file sw.js
 * @description Service Worker for Alarmissimo PWA
 * Handles caching and offline functionality
 */

const CACHE_NAME = 'alarmissimo-v3'
const urlsToCache = [
  './',
  './index.html',
  './alarmissimo.js',
  './alarmissimo.css',
  './alarmissimo.webmanifest',
  './sound/temple-bell.mp3',
  './sound/chime.mp3',
  './sound/door-bell.mp3'
]

/**
 * Install event - cache essential resources
 */
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(urlsToCache)
    })
  )
})

/**
 * Fetch event - serve from cache, fall back to network
 */
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request).then((response) => {
      if (response) {
        return response
      }
      return fetch(event.request).then((response) => {
        // Cache successful responses
        if (!response || response.status !== 200 || response.type !== 'basic') {
          return response
        }
        const responseToCache = response.clone()
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, responseToCache)
        })
        return response
      }).catch(() => {
        // Offline fallback
        return caches.match('./index.html')
      })
    })
  )
})

/**
 * Activate event - clean up old caches
 */
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheName !== CACHE_NAME) {
            return caches.delete(cacheName)
          }
          return Promise.resolve()
        })
      )
    })
  )
})
