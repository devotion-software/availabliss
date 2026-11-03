-- TIMESTAMP → DATETIME converts through the session zone: pin it to UTC so stored instants don't shift.
SET time_zone = '+00:00';--> statement-breakpoint
ALTER TABLE `busy_intervals` MODIFY COLUMN `start` datetime(3) NOT NULL, MODIFY COLUMN `end` datetime(3) NOT NULL, ADD `event_key` varchar(32) DEFAULT '' NOT NULL;--> statement-breakpoint
ALTER TABLE `calendar_connections` ADD `covered_until` datetime(3);--> statement-breakpoint
ALTER TABLE `calendar_connections` ADD `far_synced_at` timestamp(3);--> statement-breakpoint
ALTER TABLE `calendars` ADD `sync_cursor` varchar(255);--> statement-breakpoint
CREATE INDEX `busy_intervals_event_idx` ON `busy_intervals` (`calendar_id`,`event_key`);
