ALTER TABLE `calendar_connections` ADD `next_sync_at` timestamp(3);--> statement-breakpoint
ALTER TABLE `calendar_connections` ADD `failures` int DEFAULT 0 NOT NULL;--> statement-breakpoint
CREATE INDEX `calendar_connections_next_sync_idx` ON `calendar_connections` (`next_sync_at`);