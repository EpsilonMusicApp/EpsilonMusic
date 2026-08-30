# Epsilon Music — Supabase Backend

This directory contains the **complete backend** for Epsilon Music's user-owned
cloud data: authentication, profiles, cloud playlists, liked songs, listening
history, cross-device sync, and user-uploaded assets (avatars, playlist art).

## Project Reference

| Field             | Value                                              |
| ----------------- | -------------------------------------------------- |
| Project URL       | `https://ztxkyzstgeckbsfagqco.supabase.co`         |
| Region            | `ap-northeast-2` (Seoul)                           |
| PostgreSQL version| 17.6                                               |
| Pooler (session)  | `aws-0-ap-northeast-2.pooler.supabase.com:5432`    |

> The anon/publishable key is shipped inside the Android app (via
> `BuildConfig.SUPABASE_ANON_KEY`). The **service_role key** and **database
> password** are NEVER embedded in the app — they are server-side secrets.

## Schema Overview

11 user-owned tables + 1 migration tracking table:

| Table                | Purpose                                                        |
| -------------------- | -------------------------------------------------------------- |
| `profiles`           | 1:1 with `auth.users(id)`. Public identity (username, avatar). |
| `user_settings`      | Cloud-synchronized preferences (theme, language, sort orders). |
| `playlists`          | User-owned cloud playlists (title, art, public/private).       |
| `playlist_tracks`    | Ordered tracks inside a playlist. Provider-aware.              |
| `liked_songs`        | Per-user likes. Unique on `(user_id, provider, song_id)`.      |
| `saved_albums`       | Library album bookmarks. Provider-aware.                       |
| `saved_artists`      | Library artist subscriptions. Provider-aware.                  |
| `listening_history`  | Raw playback events (started/played/completed/skipped).        |
| `recently_played`    | Compact, capped (100/user) recently-played view. Auto-maintained.|
| `devices`            | Registered user devices for cross-device sync.                 |
| `user_sync_state`    | Per-entity sync watermarks.                                    |
| `schema_migrations_epsilon` | Audit log of applied migrations. (No RLS.)              |

See [SCHEMA.md](./SCHEMA.md) for the entity-relationship diagram and per-table
column reference.

## Authentication

Supabase Auth is used as the **single identity provider** for cloud data.

Enabled methods:
- Email / password (sign-up, sign-in, password reset, email verification)

Ready to be enabled in the Supabase dashboard (no schema change required):
- Google OAuth
- Apple OAuth
- Magic link
- Phone / SMS

The existing YouTube Music cookie-based login (`LoginScreen.kt` +
`AccountSettingsViewModel`) is intentionally untouched — it is a separate auth
surface for streaming. Supabase Auth is for cloud user data.

## Storage

Two buckets, both public-read but owner-write:

| Bucket                  | Purpose              | Size limit | Allowed MIME types                | Object path                    |
| ----------------------- | -------------------- | ---------- | --------------------------------- | ------------------------------ |
| `epsilon-avatars`       | Profile pictures     | 5 MB       | `image/jpeg`, `image/png`, `image/webp` | `user_id/avatar.<ext>`         |
| `epsilon-playlist-art`  | Playlist cover art   | 10 MB      | `image/jpeg`, `image/png`, `image/webp` | `user_id/playlist_id/cover.<ext>` |

Storage RLS policies enforce that `(storage.foldername(name))[1] = auth.uid()`
on all write operations, so users can never overwrite another user's assets.

## Row Level Security (RLS)

**Every user-owned table has RLS enabled** with the following pattern:

| Operation | Rule                                                            |
| --------- | --------------------------------------------------------------- |
| SELECT    | `auth.uid() = user_id` (or public for `profiles` / public playlists) |
| INSERT    | `WITH CHECK (auth.uid() = user_id)`                             |
| UPDATE    | `USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id)`|
| DELETE    | `USING (auth.uid() = user_id)`                                  |

The `playlist_tracks` table inherits ownership from its parent `playlists`
row via an `EXISTS` subquery, so a user can only modify tracks in playlists
they own.

44 RLS policies are defined in total. See migration files 002–012 for the
per-table definitions.

## Triggers

| Trigger                                  | Table              | Fires           | Effect                                            |
| ---------------------------------------- | ------------------ | --------------- | ------------------------------------------------- |
| `on_auth_user_created`                   | `auth.users`       | AFTER INSERT    | Auto-creates a `profiles` row.                    |
| `on_profile_created`                     | `profiles`         | AFTER INSERT    | Auto-creates a `user_settings` row.               |
| `*_set_updated_at`                       | (7 tables)         | BEFORE UPDATE   | Refreshes `updated_at = NOW()`.                   |
| `listening_history_upsert_recent`        | `listening_history`| AFTER INSERT    | Upserts the song into `recently_played` and bumps `play_count`. |
| `recently_played_cap`                    | `recently_played`  | AFTER INSERT/UPDATE | Caps the table at 100 rows per user (drops oldest). |
| `playlist_tracks_repack_after_delete`    | `playlist_tracks`  | AFTER DELETE    | Re-packs `position` to be gapless.                |

## Database Functions / RPCs

Exposed via PostgREST under `/rest/v1/rpc/`:

| Function                  | Purpose                                                    |
| ------------------------- | ---------------------------------------------------------- |
| `like_song`               | Idempotent upsert of a liked song.                         |
| `unlike_song`             | Remove a like.                                             |
| `record_play`             | Log a playback event (auto-updates `recently_played`).     |
| `move_playlist_track`     | Atomic reordering of a track within a playlist.            |
| `get_recently_played`     | Compact recently-played list (default limit 50, max 100).  |
| `get_user_sync_state`     | One-shot diff of all user data since a timestamp.          |
| `upsert_user_settings`    | Sparse upsert of user settings (only non-null fields).     |
| `register_device`         | Idempotent device registration.                            |
| `prune_old_listening_history` | Delete history older than 365 days (scheduled hourly). |

## Migrations

Ordered, idempotent migrations live under [`migrations/`](./migrations/).
Each statement uses `CREATE OR REPLACE` / `IF NOT EXISTS` / `ON CONFLICT DO
NOTHING`, so re-running is safe.

Applied migrations are tracked in `public.schema_migrations_epsilon`.

```
001_extensions.sql          Extensions + helper functions
002_profiles.sql            profiles + auth trigger + RLS
003_user_settings.sql       user_settings + profile trigger + RLS
004_playlists.sql           playlists + RLS
005_playlist_tracks.sql     playlist_tracks + repack trigger + RLS
006_liked_songs.sql         liked_songs + RLS
007_saved_albums.sql        saved_albums + RLS
008_saved_artists.sql       saved_artists + RLS
009_listening_history.sql   listening_history + prune function + RLS
010_recently_played.sql     recently_played + upsert/cap triggers + RLS
011_devices.sql             devices + RLS
012_user_sync_state.sql     user_sync_state + RLS
013_storage_buckets.sql     epsilon-avatars + epsilon-playlist-art buckets
014_storage_policies.sql    Storage RLS policies (owner-only writes)
015_functions.sql           RPC functions (like_song, record_play, ...)
016_realtime.sql            Add user tables to supabase_realtime publication
```

## Local Development

### Prerequisites

- A Supabase project (you can use the existing `ztxkyzstgeckbsfagqco` project
  or create a new one).
- For local Postgres testing: `psql` or any SQL client.

### Applying migrations

Use the project's migration script:

```bash
python3 /home/z/my-project/scripts/apply_migrations.py
```

Or apply individual files manually via the Supabase SQL Editor:

```sql
\i supabase/migrations/001_extensions.sql
\i supabase/migrations/002_profiles.sql
-- ...
```

### Connecting directly

```bash
psql "postgresql://postgres.ztxkyzstgeckbsfagqco:<password>@aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres"
```

## Android Integration

The Android client lives under `app/src/main/kotlin/com/music/epsilon/supabase/`:

```
di/
  SupabaseClientProvider.kt   Singleton SupabaseClient (OkHttp engine)
  SupabaseModule.kt           Hilt module exposing individual plugins
model/
  Dtos.kt                     kotlinx.serialization DTOs (mirror the SQL schema)
repository/
  AuthRepository.kt           sign-up / sign-in / sign-out / password reset
  UserRepository.kt           profile CRUD
  UserSettingsRepository.kt   syncable settings upsert/fetch
  CloudPlaylistRepository.kt  playlist + track CRUD + move RPC
  LibrarySyncRepository.kt    likes / saved albums / saved artists
  HistorySyncRepository.kt    record_play + recently_played access
  StorageRepository.kt        avatar + playlist-art upload/delete
sync/
  SyncManager.kt              Background orchestrator (pull cloud → Room)
```

### Architecture

```
        UI (Compose)
           ↓
      ViewModel (@HiltViewModel)
           ↓
      Repository (Supabase + Room)
           ↓
      Room Database (offline source of truth)
           ↓
      SyncManager (background)
           ↓
      Supabase (PostgREST + Auth + Storage + Realtime)
```

The app remains fully functional offline. The `SyncManager` pulls cloud deltas
into Room when network is available, and pushes local-only changes up. Conflict
resolution uses **last-writer-wins** on `updated_at` timestamps.

### How the Android app authenticates

1. User enters email + password in the new Epsilon sign-in screen.
2. `AuthRepository.signInWithEmail()` calls `auth.signInWith(Email)`.
3. The Supabase SDK stores the session in its encrypted local storage.
4. On next app start, `AuthRepository.loadSession()` restores the session.
5. All subsequent repository calls automatically include the JWT.
6. RLS policies on the server enforce that the user can only access their own
   data.

### How cloud sync works

1. App start → `SyncManager.triggerFullSync()` (if signed in).
2. `SyncManager` calls the `get_user_sync_state(p_since)` RPC with the last
   sync watermark (from `LastEpsilonSyncKey` DataStore preference).
3. The server returns a JSONB diff of all changed rows since `p_since`.
4. `SyncManager` iterates the diff and merges each entity into local Room:
   - Playlists: insert if not already local (preserve local edits).
   - Tracks: upsert song entity + insert playlist_song_map.
   - Liked songs: upsert song entity with `liked = true`.
   - Saved albums/artists: upsert album/artist entity.
5. The server's `server_time` is persisted as the next sync watermark.
6. Local writes (like, play, playlist add) also fire-and-forget to the cloud
   via `pushLike` / `pushPlayEvent` / `pushPlaylist`. Failures are tolerated —
   the next full sync reconciles.

## Environment Variables

Required for building the Android app:

| Variable             | Source                                | Notes                                              |
| -------------------- | ------------------------------------- | -------------------------------------------------- |
| `SUPABASE_URL`       | `local.properties` or env             | Defaults to the production project URL.            |
| `SUPABASE_ANON_KEY`  | `local.properties` or env             | Defaults to the project anon key.                  |

Server-side secrets (NEVER in the APK):

| Secret                    | Where it lives                          |
| ------------------------- | --------------------------------------- |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase dashboard / CI secrets only  |
| Database password         | Supabase dashboard / password manager   |
| Supabase Management API key | Supabase dashboard / CI secrets only  |

## What was intentionally left unchanged

Per the project requirements, the following systems were NOT modified:

- Jetpack Compose frontend
- Media3 / ExoPlayer playback engine
- YouTube Music streaming (InnerTube)
- Existing search implementation
- Lyrics providers (lrclib, kugou, betterlyrics, simpmusic, youlyplus,
  paxsenixlyrics, unison)
- Local music playback
- Downloads / audio export
- Queue system (YouTubeQueue, LocalAlbumRadio, etc.)
- Equalizer (parametric EQ, AutomixDuck)
- Widgets (music, playlist, turntable, recognizer)
- Existing music discovery (Home, Explore, MoodAndGenres, Charts, NewRelease)
- YouTube Music cookie-based login (`LoginScreen.kt`)
- Existing `SyncUtils.kt` (YouTube Music sync — runs in parallel)
- Listen Together WebSocket protocol
- Discord RPC, Last.fm, ListenBrainz, Spotify import
- Firebase Crashlytics / Analytics (GMS flavor)
- All Room entities, DAOs, and 44 existing migrations

## Deployment

The schema is already deployed to the production Supabase project
(`ztxkyzstgeckbsfagqco`). To deploy to a new project:

1. Create a new Supabase project.
2. Run `python3 /home/z/my-project/scripts/apply_migrations.py` (after
   updating the connection params at the top of the script).
3. Update `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties`.
4. Build the Android app.

To roll back a migration, write a new "down" migration file (e.g.
`017_rollback_xxx.sql`) with the inverse operations. Never edit an
already-applied migration in place.
