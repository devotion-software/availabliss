ALTER TABLE `group_invites` ADD `max_uses` int DEFAULT 1;--> statement-breakpoint
ALTER TABLE `group_invites` ADD `uses` int DEFAULT 0 NOT NULL;