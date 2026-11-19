ALTER TABLE `message_attachments` MODIFY COLUMN `kind` enum('image','gif','file','pass','video') NOT NULL;--> statement-breakpoint
ALTER TABLE `message_attachments` ADD `duration` int;