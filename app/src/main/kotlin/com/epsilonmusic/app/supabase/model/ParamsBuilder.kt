package com.epsilonmusic.app.supabase.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Helper for building kotlinx.serialization `JsonObject` parameters for Supabase
 * RPC calls. The Supabase Kotlin SDK's `rpc(function, parameters)` overload
 * requires a `JsonObject`, not a `Map<String, Any?>`.
 *
 * Usage:
 * ```kotlin
 * postgrest.rpc(
 *     function = "like_song",
 *     parameters = buildParams {
 *         put("p_song_id", songId)
 *         put("p_title", title)
 *         put("p_duration_ms", durationMs)
 *     }
 * )
 * ```
 */
fun buildParams(block: ParamsBuilder.() -> Unit): JsonObject {
    return ParamsBuilder().apply(block).build()
}

class ParamsBuilder {
    private val map = mutableMapOf<String, JsonElement>()

    fun put(key: String, value: String?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun put(key: String, value: Int?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun put(key: String, value: Long?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun put(key: String, value: Boolean?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun put(key: String, value: Float?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun put(key: String, value: Double?) {
        if (value == null) {
            map[key] = JsonNull
        } else {
            map[key] = JsonPrimitive(value)
        }
    }

    fun build(): JsonObject = JsonObject(map)
}
