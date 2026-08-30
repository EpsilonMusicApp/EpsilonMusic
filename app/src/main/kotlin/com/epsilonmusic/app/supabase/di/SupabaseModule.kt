package com.epsilonmusic.app.supabase.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import javax.inject.Singleton

/**
 * Hilt module that exposes individual Supabase plugins (Auth, Postgrest, etc.)
 * from the singleton [SupabaseClientProvider]. Repositories inject only the
 * plugin they need — this keeps the dependency surface narrow.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(provider: SupabaseClientProvider): SupabaseClient =
        provider.client

    @Provides
    @Singleton
    fun provideAuth(provider: SupabaseClientProvider): Auth = provider.client.auth

    @Provides
    @Singleton
    fun providePostgrest(provider: SupabaseClientProvider): Postgrest = provider.client.postgrest

    @Provides
    @Singleton
    fun provideStorage(provider: SupabaseClientProvider): Storage = provider.client.storage

    @Provides
    @Singleton
    fun provideRealtime(provider: SupabaseClientProvider): Realtime = provider.client.realtime

    @Provides
    @Singleton
    fun provideFunctions(provider: SupabaseClientProvider): Functions = provider.client.functions
}
