-- 013_storage_buckets.sql
-- Create the two storage buckets Epsilon Music needs.
-- Bucket creation is idempotent: insert-or-do-nothing on storage.buckets.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'epsilon-avatars',
    'epsilon-avatars',
    true,                                       -- public-read (avatars visible to other users)
    5242880,                                    -- 5 MB cap
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'epsilon-playlist-art',
    'epsilon-playlist-art',
    true,                                       -- public-read (so shared playlists can show art)
    10485760,                                   -- 10 MB cap
    ARRAY['image/jpeg', 'image/png', 'image/webp']
) ON CONFLICT (id) DO NOTHING;
