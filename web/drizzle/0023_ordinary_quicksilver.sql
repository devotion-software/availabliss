CREATE TABLE `message_attachments` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`message_id` varchar(36),
	`uploader_id` varchar(36),
	`kind` enum('image','gif','file') NOT NULL,
	`name` varchar(255) NOT NULL,
	`mime` varchar(127) NOT NULL,
	`size` int NOT NULL,
	`width` int,
	`height` int,
	`storage_key` varchar(255) NOT NULL,
	`thumb_key` varchar(255),
	`position` int NOT NULL DEFAULT 0,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `message_attachments_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `message_attachments` ADD CONSTRAINT `message_attachments_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `message_attachments` ADD CONSTRAINT `message_attachments_message_id_messages_id_fk` FOREIGN KEY (`message_id`) REFERENCES `messages`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `message_attachments` ADD CONSTRAINT `message_attachments_uploader_id_user_id_fk` FOREIGN KEY (`uploader_id`) REFERENCES `user`(`id`) ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX `message_attachments_message_idx` ON `message_attachments` (`message_id`);--> statement-breakpoint
CREATE INDEX `message_attachments_group_idx` ON `message_attachments` (`group_id`);--> statement-breakpoint
CREATE INDEX `message_attachments_pending_idx` ON `message_attachments` (`uploader_id`,`created_at`);