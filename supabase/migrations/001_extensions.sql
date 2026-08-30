-- 001_extensions.sql
-- Enable required extensions for the Epsilon Music backend.
-- All extensions are already bundled with Supabase; we just enable them.

-- pgcrypto: for gen_random_uuid() and cryptographic helpers
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- pgjwt: for JWT handling (used by Supabase Auth/RLS)
CREATE EXTENSION IF NOT EXISTS pgjwt;

-- uuid-ossp: alternative UUID generators (belt-and-suspenders)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- plpgsql: should be enabled by default
CREATE EXTENSION IF NOT EXISTS plpgsql;

-- updated_at helper function: keeps updated_at columns in sync automatically
-- Used by triggers across all tables.
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Helper: safely compare auth.uid() with a column
CREATE OR REPLACE FUNCTION public.is_owner(row_user_id UUID)
RETURNS BOOLEAN AS $$
    SELECT row_user_id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- Helper: returns the current user's id (NULL if not authenticated)
CREATE OR REPLACE FUNCTION public.current_user_id()
RETURNS UUID AS $$
    SELECT auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;
