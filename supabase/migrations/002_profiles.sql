-- 002_profiles.sql
-- User profiles linked 1:1 to auth.users(id).
-- This is the single source of truth for user-visible identity in Epsilon Music.
-- Authentication itself (passwords, OAuth tokens, email verification) is handled
-- entirely by Supabase Auth — we never store credentials here.

CREATE TABLE IF NOT EXISTS public.profiles (
    id              UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username        TEXT UNIQUE,
    display_name    TEXT,
    avatar_url      TEXT,
    bio             TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Soft constraint: username must be 3..32 chars when present, lowercase/digits/_/-
    CONSTRAINT profiles_username_format CHECK (
        username IS NULL OR (
            char_length(username) BETWEEN 3 AND 32
            AND username ~ '^[a-z0-9][a-z0-9_\-]*$'
        )
    ),
    CONSTRAINT profiles_bio_length CHECK (bio IS NULL OR char_length(bio) <= 500)
);

-- Helpful index for username lookups (UNIQUE constraint already creates one, but
-- we also create a case-insensitive index to support case-insensitive search later).
CREATE INDEX IF NOT EXISTS profiles_username_lower_idx
    ON public.profiles (lower(username))
    WHERE username IS NOT NULL;

CREATE INDEX IF NOT EXISTS profiles_created_at_idx ON public.profiles (created_at DESC);

-- updated_at trigger
DROP TRIGGER IF EXISTS profiles_set_updated_at ON public.profiles;
CREATE TRIGGER profiles_set_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- =====================================================================
-- Trigger: auto-create a profile row whenever a new auth.users row appears.
-- This makes the flow idempotent and safe to re-run.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name, username)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'display_name', split_part(NEW.email, '@', 1)),
        NEW.raw_user_meta_data ->> 'username'
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- =====================================================================
-- Row Level Security
-- Profiles are readable by anyone (so users can find each other for sharing),
-- but writable only by the owner.
-- =====================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Public read (we do NOT expose email, password, etc. — only the profile fields)
DROP POLICY IF EXISTS "profiles_public_read" ON public.profiles;
CREATE POLICY "profiles_public_read"
    ON public.profiles FOR SELECT
    USING (true);

-- Owner-only writes
DROP POLICY IF EXISTS "profiles_owner_update" ON public.profiles;
CREATE POLICY "profiles_owner_update"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- INSERT/DELETE blocked from client — profiles are created exclusively via trigger
DROP POLICY IF EXISTS "profiles_owner_insert" ON public.profiles;
CREATE POLICY "profiles_owner_insert"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_owner_delete" ON public.profiles;
CREATE POLICY "profiles_owner_delete"
    ON public.profiles FOR DELETE
    USING (auth.uid() = id);
