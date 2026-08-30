package com.music.epsilon.supabase.repository

import io.github.jan.supabase.storage.Storage
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Supabase Storage for user-owned assets. Two buckets are configured:
 *   - `epsilon-avatars`        (user profile pictures, public-read)
 *   - `epsilon-playlist-art`   (playlist cover art, public-read)
 *
 * Both buckets enforce owner-only writes via storage RLS policies (see
 * migration 014_storage_policies.sql). Object paths are `user_id/...` so the
 * policy can enforce ownership using `storage.foldername(name)[1] = auth.uid()`.
 */
@Singleton
class StorageRepository @Inject constructor(
    private val storage: Storage,
) {
    private val avatarsBucket get() = storage.from("epsilon-avatars")
    private val playlistArtBucket get() = storage.from("epsilon-playlist-art")

    /**
     * Upload the user's avatar. Returns the public URL on success.
     * `userId` must match the currently-authenticated Supabase user — this is
     * enforced server-side by the storage RLS policy.
     */
    suspend fun uploadAvatar(
        userId: UUID,
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null,
    ): String? {
        val ext = extensionForMime(mimeType) ?: "jpg"
        val path = "${userId}/avatar.$ext"
        return uploadInternal(avatarsBucket, path, bytes, mimeType, onProgress)
    }

    /**
     * Upload cover art for a specific playlist. The path encodes both the user
     * id (for RLS) and the playlist id (for uniqueness / lookup).
     */
    suspend fun uploadPlaylistArt(
        userId: UUID,
        playlistId: UUID,
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null,
    ): String? {
        val ext = extensionForMime(mimeType) ?: "jpg"
        val path = "${userId}/${playlistId}/cover.$ext"
        return uploadInternal(playlistArtBucket, path, bytes, mimeType, onProgress)
    }

    private suspend fun uploadInternal(
        bucket: io.github.jan.supabase.storage.BucketApi,
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: ((Float) -> Unit)?,
    ): String? = try {
        bucket.upload(path, bytes) {
            upsert = true
            this.contentType = mimeType
        }
        bucket.publicUrl(path)
    } catch (e: Exception) {
        Timber.e(e, "Storage upload failed for path=$path")
        null
    }

    suspend fun deleteAvatar(userId: UUID, mimeType: String): Boolean {
        val ext = extensionForMime(mimeType) ?: "jpg"
        return deleteInternal(avatarsBucket, "${userId}/avatar.$ext")
    }

    suspend fun deletePlaylistArt(userId: UUID, playlistId: UUID, mimeType: String): Boolean {
        val ext = extensionForMime(mimeType) ?: "jpg"
        return deleteInternal(playlistArtBucket, "${userId}/${playlistId}/cover.$ext")
    }

    private suspend fun deleteInternal(
        bucket: io.github.jan.supabase.storage.BucketApi,
        path: String,
    ): Boolean = try {
        bucket.delete(path)
        true
    } catch (e: Exception) {
        Timber.w(e, "Storage delete failed for path=$path")
        false
    }

    fun publicAvatarUrl(userId: UUID, mimeType: String = "image/jpeg"): String {
        val ext = extensionForMime(mimeType) ?: "jpg"
        return avatarsBucket.publicUrl("${userId}/avatar.$ext")
    }

    fun publicPlaylistArtUrl(userId: UUID, playlistId: UUID, mimeType: String = "image/jpeg"): String {
        val ext = extensionForMime(mimeType) ?: "jpg"
        return playlistArtBucket.publicUrl("${userId}/${playlistId}/cover.$ext")
    }

    private fun extensionForMime(mime: String): String? = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> null
    }
}
