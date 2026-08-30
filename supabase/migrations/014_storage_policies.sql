-- 014_storage_policies.sql
-- Storage RLS policies: users can only CRUD their own assets, organised under
-- a `user_id/...` object path.

-- =====================================================================
-- epsilon-avatars bucket
-- Allowed object path: "<user_id>/<anything>"
-- =====================================================================

-- SELECT (read): anyone can read avatars (they are public)
DROP POLICY IF EXISTS "avatars_public_read" ON storage.objects;
CREATE POLICY "avatars_public_read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'epsilon-avatars');

-- INSERT: only the owner, and only into their own folder
DROP POLICY IF EXISTS "avatars_owner_insert" ON storage.objects;
CREATE POLICY "avatars_owner_insert"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'epsilon-avatars'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- UPDATE: only the owner can replace their avatar
DROP POLICY IF EXISTS "avatars_owner_update" ON storage.objects;
CREATE POLICY "avatars_owner_update"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'epsilon-avatars'
        AND (storage.foldername(name))[1] = auth.uid()::text
    )
    WITH CHECK (
        bucket_id = 'epsilon-avatars'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- DELETE: only the owner
DROP POLICY IF EXISTS "avatars_owner_delete" ON storage.objects;
CREATE POLICY "avatars_owner_delete"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'epsilon-avatars'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- =====================================================================
-- epsilon-playlist-art bucket
-- Object path: "<user_id>/<playlist_id>/<filename>"
-- =====================================================================

DROP POLICY IF EXISTS "playlist_art_public_read" ON storage.objects;
CREATE POLICY "playlist_art_public_read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'epsilon-playlist-art');

DROP POLICY IF EXISTS "playlist_art_owner_insert" ON storage.objects;
CREATE POLICY "playlist_art_owner_insert"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'epsilon-playlist-art'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "playlist_art_owner_update" ON storage.objects;
CREATE POLICY "playlist_art_owner_update"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'epsilon-playlist-art'
        AND (storage.foldername(name))[1] = auth.uid()::text
    )
    WITH CHECK (
        bucket_id = 'epsilon-playlist-art'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "playlist_art_owner_delete" ON storage.objects;
CREATE POLICY "playlist_art_owner_delete"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'epsilon-playlist-art'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
