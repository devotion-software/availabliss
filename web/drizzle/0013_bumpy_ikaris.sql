ALTER TABLE `notifications` MODIFY COLUMN `kind` enum('proposal.created','proposal.confirmed','nudge','invite.accepted','invite.received','message','event.created') NOT NULL;--> statement-breakpoint
ALTER TABLE `user` ADD `username` varchar(32);--> statement-breakpoint
ALTER TABLE `group_invites` ADD `invited_user_id` varchar(36);--> statement-breakpoint
ALTER TABLE `user` ADD CONSTRAINT `user_username_unique` UNIQUE(`username`);--> statement-breakpoint
ALTER TABLE `group_invites` ADD CONSTRAINT `group_invites_invited_user_id_user_id_fk` FOREIGN KEY (`invited_user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;