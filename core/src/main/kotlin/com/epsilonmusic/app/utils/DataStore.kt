package com.epsilonmusic.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.epsilonmusic.app.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * In-memory snapshots of each store's latest preferences.
 *
 * The synchronous [get] below used to run `runBlocking { data.first() }` on
 * EVERY read — and the `preference`/`enumPreference` delegates call it on every
 * property access (100+ call sites: view models, services, composition). Each
 * read blocked the calling thread on the DataStore actor, which adds up to
 * visible jank: main-thread stalls during composition and hiccups in playback
 * code that reads settings on song changes.
 *
 * Now the first read of a store starts a collector that keeps a volatile
 * snapshot current, and reads are served from memory. The blocking path
 * remains only as a fallback for the very first read before the collector
 * has emitted, and it seeds the snapshot itself.
 */
private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val snapshots: MutableMap<DataStore<Preferences>, Preferences> =
    Collections.synchronizedMap(WeakHashMap())
private val snapshotStarted: MutableSet<DataStore<Preferences>> =
    Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<DataStore<Preferences>, Boolean>()))

private fun DataStore<Preferences>.ensureSnapshot() {
    if (!snapshotStarted.add(this)) return
    snapshotScope.launch {
        try {
            data.collect { snapshots[this@ensureSnapshot] = it }
        } catch (e: Exception) {
            snapshotStarted.remove(this@ensureSnapshot)
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? {
    ensureSnapshot()
    snapshots[this]?.let { return it[key] }
    return runBlocking(Dispatchers.IO) {
        (try {
            val prefs = data.first()
            snapshots[this@get] = prefs
            prefs[key]
        } catch (e: Exception) { null })
    }
}

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = this[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> (try { context.dataStore[key] } catch(e: Exception) { null }) ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> (try { context.dataStore[key] } catch(e: Exception) { null }).toEnum(defaultValue) }
