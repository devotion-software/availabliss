CREATE TABLE `busy_ignores` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`calendar_id` varchar(36) NOT NULL,
	`event_key` varchar(64) NOT NULL DEFAULT '',
	`start` datetime(3),
	`end` datetime(3),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `busy_ignores_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `busy_ignores` ADD CONSTRAINT `busy_ignores_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `busy_ignores` ADD CONSTRAINT `busy_ignores_calendar_id_calendars_id_fk` FOREIGN KEY (`calendar_id`) REFERENCES `calendars`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX `busy_ignores_user_idx` ON `busy_ignores` (`user_id`);--> statement-breakpoint
CREATE INDEX `busy_ignores_event_idx` ON `busy_ignores` (`calendar_id`,`event_key`);