CREATE TABLE `user_plans` (
	`user_id` varchar(36) NOT NULL,
	`pro_until` timestamp(3),
	`lifetime` boolean NOT NULL DEFAULT false,
	`store` varchar(32),
	`product_id` varchar(128),
	`will_renew` boolean NOT NULL DEFAULT true,
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `user_plans_user_id` PRIMARY KEY(`user_id`)
);
--> statement-breakpoint
ALTER TABLE `user_plans` ADD CONSTRAINT `user_plans_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;