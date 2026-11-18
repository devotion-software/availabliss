CREATE TABLE `calendar_feeds` (
	`user_id` varchar(36) NOT NULL,
	`token_hash` varchar(64) NOT NULL,
	`token_sealed` text NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `calendar_feeds_user_id` PRIMARY KEY(`user_id`),
	CONSTRAINT `calendar_feeds_token_hash_unique` UNIQUE(`token_hash`)
);
--> statement-breakpoint
ALTER TABLE `calendar_feeds` ADD CONSTRAINT `calendar_feeds_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;