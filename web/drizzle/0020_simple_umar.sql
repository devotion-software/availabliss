ALTER TABLE `groups` ADD `auto_archive` boolean DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE `groups` ADD `auto_archived_through` timestamp(3);