@file:Suppress("PackageDirectoryMismatch")

package java.util.concurrent

/** The browser is single-threaded, a plain HashMap is enough. */
typealias ConcurrentHashMap<K, V> = HashMap<K, V>
