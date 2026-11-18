ALTER TABLE `poll_options` ADD `start` datetime(3);--> statement-breakpoint
ALTER TABLE `poll_options` ADD `end` datetime(3);--> statement-breakpoint
ALTER TABLE `poll_options` ADD `all_day` boolean DEFAULT false NOT NULL;