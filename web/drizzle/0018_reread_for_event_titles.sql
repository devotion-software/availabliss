-- Event names were added after many calendars had synced, and change-tracked calendars only re-read
-- changed events, so older events would never get a name. Forget every cursor and make every
-- server-side connection sync now: each calendar is read in full once and its names filled in.
UPDATE `calendars` SET `sync_cursor` = NULL;
--> statement-breakpoint
UPDATE `calendar_connections` SET `next_sync_at` = NULL WHERE `provider` <> 'device';
