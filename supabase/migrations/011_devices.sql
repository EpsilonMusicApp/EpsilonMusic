-- 011_devices.sql
-- Per-user registered devices. Used for cross-device sync coordination and
-- "Log out other devices" / "Active sessions" features.

CREATE TABLE IF NOT EXISTS public.devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    -- A client-generated stable device id (so the same physical device reuses
    -- the same row across reinstalls if the user restores a backup)
    device_fingerprint TEXT NOT NULL,
    device_name     TEXT,
    device_type     TEXT NOT NULL DEFAULT 'android'
                    CHECK (device_type IN ('android', 'ios', 'web', 'desktop', 'other')),
    platform        TEXT,
    app_version     TEXT,
    os_version      TEXT,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_ip    INET,
    push_token      TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT devices_user_fingerprint_unique UNIQUE (user_id, device_fingerprint)
);

CREATE INDEX IF NOT EXISTS devices_user_idx ON public.devices (user_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS devices_active_idx ON public.devices (user_id) WHERE is_active = true;

DROP TRIGGER IF EXISTS devices_set_updated_at ON public.devices;
CREATE TRIGGER devices_set_updated_at
    BEFORE UPDATE ON public.devices
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- =====================================================================
-- RLS: owner-only.
-- =====================================================================
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "devices_owner_select" ON public.devices;
CREATE POLICY "devices_owner_select"
    ON public.devices FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "devices_owner_insert" ON public.devices;
CREATE POLICY "devices_owner_insert"
    ON public.devices FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "devices_owner_update" ON public.devices;
CREATE POLICY "devices_owner_update"
    ON public.devices FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "devices_owner_delete" ON public.devices;
CREATE POLICY "devices_owner_delete"
    ON public.devices FOR DELETE
    USING (auth.uid() = user_id);
