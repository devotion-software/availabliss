CREATE TABLE `message_reactions` (
	`message_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`emoji` varchar(64) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `message_reactions_message_id_user_id_pk` PRIMARY KEY(`message_id`,`user_id`)
);
--> statement-breakpoint
ALTER TABLE `message_reactions` ADD CONSTRAINT `message_reactions_message_id_messages_id_fk` FOREIGN KEY (`message_id`) REFERENCES `messages`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `message_reactions` ADD CONSTRAINT `message_reactions_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;