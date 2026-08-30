# Epsilon Music — Database Schema

This document is a human-readable reference for the Epsilon Music Supabase
schema. For the authoritative definition, see the migration files under
`migrations/`.

## Entity-Relationship Diagram (textual)

```
auth.users (Supabase-managed)
    │
    │ 1:1 (ON DELETE CASCADE)
    ▼
profiles ────────────► user_settings
    │  (id = auth.users.id)        (user_id = auth.users.id, 1:1)
    │
    │ 1:N
    ▼
playlists ──────────► playlist_tracks
    │  (user_id = auth.users.id)     (playlist_id = playlists.id, ON DELETE CASCADE)
    │
    │ 1:N                            playlist_tracks also FK to (provider, song_id)
    ▼                                which is denormalized metadata, NOT a FK
liked_songs
saved_albums
saved_artists
    │  (all: user_id = auth.users.id, 1:N)
    │
    │ 1:N
    ▼
listening_history ────► recently_played
    │  (user_id = auth.users.id)       (user_id, provider, song_id UNIQUE)
    │                                  auto-maintained by trigger on
    │                                  listening_history INSERT
    │
    │ 1:N
    ▼
devices
user_sync_state (1:N per entity type)
```

## Tables

### `profiles`

User-visible identity. 1:1 with `auth.users(id)`. Auto-created by the
`on_auth_user_created` trigger when a new auth user signs up.

| Column        | Type         | Notes                                                  |
| ------------- | ------------ | ------------------------------------------------------ |
| `id`          | UUID PK      | FK → `auth.users(id)` ON DELETE CASCADE                |
| `username`    | TEXT UNIQUE  | 3–32 chars, lowercase/digits/_/-, regex-validated      |
| `display_name`| TEXT         | Free-form display name                                 |
| `avatar_url`  | TEXT         | Public URL of the avatar in `epsilon-avatars` bucket   |
| `bio`         | TEXT         | Up to 500 chars                                        |
| `created_at`  | TIMESTAMPTZ  | Default `NOW()`                                        |
| `updated_at`  | TIMESTAMPTZ  | Auto-refreshed by trigger                              |

**RLS**: Public SELECT (anyone can read profiles); owner-only INSERT/UPDATE/DELETE.

### `user_settings`

Cloud-synced preferences. Auto-created by the `on_profile_created` trigger.

42 strongly-typed columns covering: theme, language, content country, audio
quality, download quality, playback engine, crossfade, autoplay, sort orders,
show/hide library shelves, etc.

Plus a `preferences JSONB` column for evolving/less-queryable settings
(e.g., lyrics provider order) and a `schema_version INTEGER` for forward
compatibility.

**RLS**: Owner-only on all operations.

### `playlists`

User-owned cloud playlists.

| Column           | Type         | Notes                                                |
| ---------------- | ------------ | ---------------------------------------------------- |
| `id`             | UUID PK      | Auto-generated                                       |
| `user_id`        | UUID         | FK → `auth.users(id)` ON DELETE CASCADE              |
| `title`          | TEXT         | 1–200 chars                                          |
| `description`    | TEXT         | Up to 2000 chars                                     |
| `artwork_url`    | TEXT         | Public URL of cover art in `epsilon-playlist-art`    |
| `is_public`      | BOOLEAN      | Default `false`                                      |
| `sort_order`     | INTEGER      | Default 0                                            |
| `share_slug`     | TEXT UNIQUE  | Optional vanity slug for shareable URLs              |
| `last_synced_at` | TIMESTAMPTZ  | Set by client after a successful sync                |
| `deleted_at`     | TIMESTAMPTZ  | Soft-delete marker (NULL = not deleted)              |
| `created_at`     | TIMESTAMPTZ  | Default `NOW()`                                      |
| `updated_at`     | TIMESTAMPTZ  | Auto-refreshed by trigger                            |

**RLS**: SELECT allows owner OR (public AND not deleted); other ops owner-only.

### `playlist_tracks`

Ordered tracks inside a playlist. Provider-aware.

| Column         | Type      | Notes                                                       |
| -------------- | --------- | ----------------------------------------------------------- |
| `id`           | UUID PK   | Auto-generated                                              |
| `playlist_id`  | UUID      | FK → `playlists(id)` ON DELETE CASCADE                      |
| `provider`     | TEXT      | `youtube` / `apple_music` / `spotify` / `local` / `unknown` |
| `song_id`      | TEXT      | Provider-specific song id                                   |
| `title`        | TEXT      | Denormalized                                                |
| `artist`       | TEXT      | Denormalized                                                |
| `album`        | TEXT      | Denormalized                                                |
| `duration_ms`  | INTEGER   | Denormalized                                                |
| `thumbnail_url`| TEXT      | Denormalized                                                |
| `set_video_id` | TEXT      | YouTube-specific                                            |
| `position`     | INTEGER   | 0-indexed, gapless (auto-repacked on delete)                |
| `added_at`     | TIMESTAMPTZ | Default `NOW()`                                           |
| `updated_at`   | TIMESTAMPTZ | Auto-refreshed                                             |

**Unique constraint**: `(playlist_id, provider, song_id)` — no duplicate songs
within a single playlist.

**RLS**: A track is accessible iff its parent playlist is accessible (via
`EXISTS` subquery).

### `liked_songs`

| Column         | Type      | Notes                              |
| -------------- | --------- | ---------------------------------- |
| `id`           | UUID PK   |                                    |
| `user_id`      | UUID      | FK → `auth.users(id)` CASCADE      |
| `provider`     | TEXT      |                                    |
| `song_id`      | TEXT      |                                    |
| `title` ... `thumbnail_url` | TEXT/INTEGER | Denormalized metadata |
| `liked_at`     | TIMESTAMPTZ | Default `NOW()`                  |

**Unique constraint**: `(user_id, provider, song_id)` — one like per song per
provider.

### `saved_albums` / `saved_artists`

Same shape as `liked_songs`: provider-aware, unique on
`(user_id, provider, album_id)` / `(user_id, provider, artist_id)`.

### `listening_history`

Raw playback events. One row per event (started/played/completed/skipped).

| Column         | Type         | Notes                                            |
| -------------- | ------------ | ------------------------------------------------ |
| `id`           | UUID PK      |                                                  |
| `user_id`      | UUID         | FK → `auth.users(id)` CASCADE                    |
| `provider`     | TEXT         |                                                  |
| `song_id`      | TEXT         |                                                  |
| `title` ... `thumbnail_url` | TEXT/INTEGER | Denormalized metadata             |
| `event_type`   | TEXT         | `started` / `played` / `completed` / `skipped`   |
| `listened_ms`  | INTEGER      | How long the user actually listened              |
| `played_at`    | TIMESTAMPTZ  | When the playback happened                       |
| `device_id`    | UUID         | Optional — references `devices.id` (no FK)       |
| `created_at`   | TIMESTAMPTZ  | Default `NOW()`                                  |

**Retention**: `prune_old_listening_history(365)` deletes rows older than 1
year. Scheduled hourly via `pg_cron` if the extension is enabled.

### `recently_played`

Compact, deduplicated view of recently played songs. Auto-maintained by the
`listening_history_upsert_recent` trigger. Capped at 100 rows per user by the
`recently_played_cap` trigger.

| Column           | Type         | Notes                                |
| ---------------- | ------------ | ------------------------------------ |
| `id`             | UUID PK      |                                      |
| `user_id`        | UUID         | FK → `auth.users(id)` CASCADE        |
| `provider`       | TEXT         |                                      |
| `song_id`        | TEXT         |                                      |
| `title` ... `thumbnail_url` | TEXT/INTEGER | Denormalized metadata   |
| `last_played_at` | TIMESTAMPTZ  | Updated on each play                 |
| `play_count`     | INTEGER      | Bumped on each play                  |
| `created_at`     | TIMESTAMPTZ  |                                      |

**Unique constraint**: `(user_id, provider, song_id)`.

This table exists so the Android home screen can fetch "Recently Played" with
a single small query (≤ 100 rows per user) instead of scanning
`listening_history`.

### `devices`

Registered user devices for cross-device sync.

| Column              | Type         | Notes                                  |
| ------------------- | ------------ | -------------------------------------- |
| `id`                | UUID PK      |                                        |
| `user_id`           | UUID         | FK → `auth.users(id)` CASCADE          |
| `device_fingerprint`| TEXT         | Client-generated stable id             |
| `device_name`       | TEXT         | User-visible name                      |
| `device_type`       | TEXT         | `android` / `ios` / `web` / `desktop`  |
| `platform`          | TEXT         |                                        |
| `app_version`       | TEXT         |                                        |
| `os_version`        | TEXT         |                                        |
| `last_seen_at`      | TIMESTAMPTZ  | Updated via `register_device` RPC      |
| `last_seen_ip`      | INET         | Auto-populated by Supabase             |
| `push_token`        | TEXT         | For future push notifications          |
| `is_active`         | BOOLEAN      | Default `true`                         |
| `created_at` / `updated_at` | TIMESTAMPTZ |                              |

**Unique constraint**: `(user_id, device_fingerprint)` — same physical device
reuses the same row across reinstalls.

### `user_sync_state`

Per-entity sync watermarks.

| Column               | Type         | Notes                                                       |
| -------------------- | ------------ | ----------------------------------------------------------- |
| `user_id`            | UUID         | FK → `auth.users(id)` CASCADE                               |
| `entity`             | TEXT         | `playlists` / `playlist_tracks` / `liked_songs` / ...       |
| `last_synced_at`     | TIMESTAMPTZ  |                                                             |
| `last_synced_version`| BIGINT       |                                                             |
| `last_sync_status`   | TEXT         | `idle` / `syncing` / `success` / `error`                    |
| `last_error`         | TEXT         |                                                             |
| `updated_at`         | TIMESTAMPTZ  |                                                             |

**Primary key**: `(user_id, entity)`.

## Provider-Awareness

All song/album/artist tables include a `provider` column with a CHECK
constraint:

```sql
CHECK (provider IN ('youtube', 'apple_music', 'spotify', 'local', 'unknown'))
```

This means a user can like the same logical song once per provider (e.g., once
on YouTube Music, once on Apple Music) without conflict. Adding a new provider
later requires only a schema migration to extend the CHECK constraint — no
table restructuring.

## Indexes (44 total)

Key indexes:

| Table              | Index                                   | Purpose                          |
| ------------------ | --------------------------------------- | -------------------------------- |
| `profiles`         | `profiles_username_lower_idx`           | Case-insensitive username search |
| `playlists`        | `playlists_user_updated_idx`            | List user playlists by recency   |
| `playlists`        | `playlists_public_idx` (partial)        | Browse public playlists          |
| `playlist_tracks`  | `playlist_tracks_playlist_position_idx` | Fetch tracks in order            |
| `liked_songs`      | `liked_songs_user_idx`                  | List user's liked songs          |
| `listening_history`| `listening_history_user_played_idx`     | Paginate history by recency      |
| `recently_played`  | `recently_played_user_last_idx`         | Home screen "Recently Played"    |
| `devices`          | `devices_user_idx`                      | List user's devices              |

## Constraints Summary

- **Foreign keys**: All user-owned tables FK to `auth.users(id)` with `ON DELETE
  CASCADE`. `playlist_tracks` additionally FK to `playlists(id)` CASCADE.
- **Unique constraints**: `(user_id, provider, song_id)` on `liked_songs`;
  `(playlist_id, provider, song_id)` on `playlist_tracks`;
  `(user_id, provider, album_id)` on `saved_albums`;
  `(user_id, provider, artist_id)` on `saved_artists`;
  `(user_id, device_fingerprint)` on `devices`;
  `(user_id, entity)` on `user_sync_state`.
- **CHECK constraints**: `provider` enum on all provider-aware tables;
  `event_type` enum on `listening_history`; `theme`/`audio_quality`/
  `download_quality`/`playback_engine` enums on `user_settings`; `position >= 0`
  on `playlist_tracks`; `crossfade_duration BETWEEN 0 AND 12`; `year BETWEEN
  1900 AND 2100`; `bio <= 500 chars`; `title 1..200 chars`.
- **NOT NULL**: All `user_id` columns, all `created_at`/`updated_at` columns,
  `title` on `playlists`, etc.
