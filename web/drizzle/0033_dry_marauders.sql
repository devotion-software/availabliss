CREATE TABLE `sent_reminders` (
	`kind` varchar(16) NOT NULL,
	`target_id` varchar(36) NOT NULL,
	`starts_at` datetime(3) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `sent_reminders_kind_target_id_starts_at_pk` PRIMARY KEY(`kind`,`target_id`,`starts_at`)
);
--> statement-breakpoint
ALTER TABLE `notifications` MODIFY COLUMN `kind` enum('proposal.created','proposal.confirmed','proposal.changed','nudge','invite.accepted','invite.received','message','event.created','role.changed','reminder') NOT NULL;--> statement-breakpoint
ALTER TABLE `user` ADD `event_reminders` varchar(8) DEFAULT 'both' NOT NULL;