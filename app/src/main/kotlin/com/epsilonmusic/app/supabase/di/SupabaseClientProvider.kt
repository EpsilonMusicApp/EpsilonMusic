package com.epsilonmusic.app.supabase.di

import com.epsilonmusic.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the Supabase Kotlin SDK client.
 *
 * Configuration:
 *  - URL + anon key are read from BuildConfig (populated from local.properties
 *    or environment variables at build time).
 *  - HTTP engine: OkHttp (already a transitive dependency of the project).
 *  - Modules installed: Auth, Postgrest, Storage, Realtime, Functions.
 *
 * The service_role key is NEVER referenced here — the Android app only ever
 * holds the anon/publishable key, and all privileged operations are gated by
 * Row Level Security on the server.
 */
@Singleton
class SupabaseClientProvider @Inject constructor() {

    val client: SupabaseClient by lazy { build() }

    private fun build(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        httpEngine = OkHttp.create {
            config {
                retryOnConnectionFailure(true)
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}
