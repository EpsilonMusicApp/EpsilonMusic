package com.music.echo.supabase.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonNull

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
    private val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    fun put(key: String, value: String?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun put(key: String, value: Int?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun put(key: String, value: Long?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun put(key: String, value: Boolean?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun put(key: String, value: Float?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun put(key: String, value: Double?) {
        if (value == null) putJsonNull(key) else map[key] = JsonPrimitive(value)
    }

    fun build(): JsonObject = JsonObject(map)
}
