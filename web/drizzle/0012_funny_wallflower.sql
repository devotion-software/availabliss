CREATE TABLE `group_preferences` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`kind` enum('dates','weekly') NOT NULL,
	`from_date` date,
	`to_date` date,
	`days` json NOT NULL,
	`window_from` varchar(5),
	`window_to` varchar(5),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `group_preferences_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `group_preferences` ADD CONSTRAINT `group_preferences_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_preferences` ADD CONSTRAINT `group_preferences_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX `group_preferences_group_idx` ON `group_preferences` (`group_id`);