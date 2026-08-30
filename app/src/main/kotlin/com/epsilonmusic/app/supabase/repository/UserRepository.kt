package com.epsilonmusic.app.supabase.repository

import com.epsilonmusic.app.supabase.model.ProfileDto
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Operations on the public.profiles table (linked 1:1 to auth.users).
 *
 * The `handle_new_user` trigger on auth.users auto-creates a profile row when
 * a new auth user signs up, so most users will already have a row by the time
 * they reach the UI. This repository handles the editing flow.
 */
@Singleton
class UserRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth,
) {
    private val table get() = postgrest.from("profiles")

    suspend fun getCurrentProfile(): ProfileDto? = try {
        val uid = auth.currentUserOrNull()?.id ?: return null
        table.select {
            filter { eq("id", uid) }
        }.decodeSingleOrNull<ProfileDto>()
    } catch (e: Exception) {
        Timber.w(e, "getCurrentProfile failed")
        null
    }

    suspend fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        bio: String? = null,
    ): ProfileDto? = try {
        val updates = buildMap {
            username?.let { put("username", it) }
            displayName?.let { put("display_name", it) }
            avatarUrl?.let { put("avatar_url", it) }
            bio?.let { put("bio", it) }
        }
        if (updates.isEmpty()) return null
        table.update(updates) {
            select()
        }.decodeSingleOrNull<ProfileDto>()
    } catch (e: Exception) {
        Timber.e(e, "updateProfile failed")
        null
    }

    suspend fun isUsernameAvailable(username: String): Boolean = try {
        postgrest.from("profiles").select {
            filter { eq("username", username) }
        }.decodeList<ProfileDto>().isEmpty()
    } catch (e: Exception) {
        Timber.w(e, "isUsernameAvailable failed")
        false
    }
}
