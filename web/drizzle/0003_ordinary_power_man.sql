CREATE TABLE `user_aliases` (
	`old_user_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `user_aliases_old_user_id` PRIMARY KEY(`old_user_id`)
);
--> statement-breakpoint
ALTER TABLE `user_aliases` ADD CONSTRAINT `user_aliases_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;