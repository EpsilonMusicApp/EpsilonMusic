-- 016_realtime.sql
-- Add user-owned tables to the Supabase Realtime publication so the Android
-- app can subscribe to live changes for instant cross-device sync.

ALTER PUBLICATION supabase_realtime ADD TABLE public.playlists;
ALTER PUBLICATION supabase_realtime ADD TABLE public.playlist_tracks;
ALTER PUBLICATION supabase_realtime ADD TABLE public.liked_songs;
ALTER PUBLICATION supabase_realtime ADD TABLE public.saved_albums;
ALTER PUBLICATION supabase_realtime ADD TABLE public.saved_artists;
ALTER PUBLICATION supabase_realtime ADD TABLE public.recently_played;
ALTER PUBLICATION supabase_realtime ADD TABLE public.user_settings;
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
