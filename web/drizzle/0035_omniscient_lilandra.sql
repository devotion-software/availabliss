CREATE TABLE `group_mutes` (
	`user_id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`until` datetime(3),
	CONSTRAINT `group_mutes_user_id_group_id_pk` PRIMARY KEY(`user_id`,`group_id`)
);
--> statement-breakpoint
ALTER TABLE `user` ADD `notification_off` json;--> statement-breakpoint
ALTER TABLE `user` ADD `quiet_hours` json;--> statement-breakpoint
ALTER TABLE `group_mutes` ADD CONSTRAINT `group_mutes_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_mutes` ADD CONSTRAINT `group_mutes_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;