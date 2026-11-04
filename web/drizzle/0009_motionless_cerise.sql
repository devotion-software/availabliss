ALTER TABLE `calendars` ADD `push_id` varchar(64);--> statement-breakpoint
ALTER TABLE `calendars` ADD `push_resource_id` varchar(255);--> statement-breakpoint
ALTER TABLE `calendars` ADD `push_expires_at` timestamp(3);--> statement-breakpoint
ALTER TABLE `calendars` ADD CONSTRAINT `calendars_push_uq` UNIQUE(`push_id`);