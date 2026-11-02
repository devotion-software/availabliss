CREATE TABLE `requirement_templates` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`name` varchar(60) NOT NULL,
	`requirement` json NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `requirement_templates_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `requirement_templates` ADD CONSTRAINT `requirement_templates_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX `requirement_templates_user_idx` ON `requirement_templates` (`user_id`);