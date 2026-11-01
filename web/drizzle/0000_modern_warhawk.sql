CREATE TABLE `account` (
	`id` varchar(36) NOT NULL,
	`account_id` text NOT NULL,
	`provider_id` text NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`access_token` text,
	`refresh_token` text,
	`id_token` text,
	`access_token_expires_at` timestamp(3),
	`refresh_token_expires_at` timestamp(3),
	`scope` text,
	`password` text,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL,
	CONSTRAINT `account_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `passkey` (
	`id` varchar(36) NOT NULL,
	`name` text,
	`public_key` text NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`credential_id` varchar(255) NOT NULL,
	`counter` int NOT NULL,
	`device_type` text NOT NULL,
	`backed_up` boolean NOT NULL,
	`transports` text,
	`created_at` timestamp(3),
	`aaguid` text,
	CONSTRAINT `passkey_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `session` (
	`id` varchar(36) NOT NULL,
	`expires_at` timestamp(3) NOT NULL,
	`token` varchar(255) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL,
	`ip_address` text,
	`user_agent` text,
	`user_id` varchar(36) NOT NULL,
	CONSTRAINT `session_id` PRIMARY KEY(`id`),
	CONSTRAINT `session_token_unique` UNIQUE(`token`)
);
--> statement-breakpoint
CREATE TABLE `user` (
	`id` varchar(36) NOT NULL,
	`name` varchar(255) NOT NULL,
	`email` varchar(255) NOT NULL,
	`email_verified` boolean NOT NULL DEFAULT false,
	`image` text,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	`theme_mode` varchar(16) NOT NULL DEFAULT 'system',
	`color_theme` varchar(16) NOT NULL DEFAULT 'dawn',
	`country` varchar(2),
	`subdivision` varchar(10),
	`school_authority` varchar(64),
	`timezone` varchar(64) NOT NULL DEFAULT 'Europe/London',
	`onboarded` boolean NOT NULL DEFAULT false,
	CONSTRAINT `user_id` PRIMARY KEY(`id`),
	CONSTRAINT `user_email_unique` UNIQUE(`email`)
);
--> statement-breakpoint
CREATE TABLE `verification` (
	`id` varchar(36) NOT NULL,
	`identifier` varchar(255) NOT NULL,
	`value` text NOT NULL,
	`expires_at` timestamp(3) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `verification_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `busy_intervals` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`calendar_id` varchar(36) NOT NULL,
	`start` timestamp(3) NOT NULL,
	`end` timestamp(3) NOT NULL,
	CONSTRAINT `busy_intervals_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `calendar_connections` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`provider` enum('google','microsoft','caldav','device') NOT NULL,
	`label` varchar(255) NOT NULL,
	`device_id` varchar(64),
	`credentials` text,
	`status` enum('ok','error','reauth_required') NOT NULL DEFAULT 'ok',
	`last_error` varchar(255),
	`last_synced_at` timestamp(3),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `calendar_connections_id` PRIMARY KEY(`id`),
	CONSTRAINT `calendar_connections_device_uq` UNIQUE(`user_id`,`device_id`)
);
--> statement-breakpoint
CREATE TABLE `calendars` (
	`id` varchar(36) NOT NULL,
	`connection_id` varchar(36) NOT NULL,
	`external_id` varchar(255) NOT NULL,
	`name` varchar(160) NOT NULL,
	`color` varchar(32),
	`busy` boolean NOT NULL DEFAULT true,
	CONSTRAINT `calendars_id` PRIMARY KEY(`id`),
	CONSTRAINT `calendars_external_uq` UNIQUE(`connection_id`,`external_id`)
);
--> statement-breakpoint
CREATE TABLE `devices` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`platform` enum('ios','android') NOT NULL,
	`token` varchar(512) NOT NULL,
	`name` varchar(80),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `devices_id` PRIMARY KEY(`id`),
	CONSTRAINT `devices_token_unique` UNIQUE(`token`)
);
--> statement-breakpoint
CREATE TABLE `events` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`proposal_id` varchar(36),
	`created_by` varchar(36) NOT NULL,
	`title` varchar(120) NOT NULL,
	`location` varchar(160),
	`note` text,
	`start` timestamp(3) NOT NULL,
	`end` timestamp(3) NOT NULL,
	`all_day` boolean NOT NULL DEFAULT false,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `events_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `group_invites` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`token` varchar(64) NOT NULL,
	`email` varchar(255),
	`created_by` varchar(36) NOT NULL,
	`expires_at` timestamp(3) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `group_invites_id` PRIMARY KEY(`id`),
	CONSTRAINT `group_invites_token_unique` UNIQUE(`token`)
);
--> statement-breakpoint
CREATE TABLE `group_members` (
	`group_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`role` enum('owner','admin','member') NOT NULL DEFAULT 'member',
	`joined_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `group_members_group_id_user_id_pk` PRIMARY KEY(`group_id`,`user_id`)
);
--> statement-breakpoint
CREATE TABLE `groups` (
	`id` varchar(36) NOT NULL,
	`name` varchar(80) NOT NULL,
	`color` varchar(8) NOT NULL DEFAULT 't1',
	`requirement` json NOT NULL,
	`created_by` varchar(36),
	`last_nudged_at` timestamp(3),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `groups_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `holiday_cache` (
	`country` varchar(2) NOT NULL,
	`year` int NOT NULL,
	`holidays` json NOT NULL,
	`fetched_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `holiday_cache_country_year_pk` PRIMARY KEY(`country`,`year`)
);
--> statement-breakpoint
CREATE TABLE `manual_blocks` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`start` timestamp(3) NOT NULL,
	`end` timestamp(3) NOT NULL,
	`all_day` boolean NOT NULL DEFAULT false,
	`repeat` enum('none','weekly','fortnightly') NOT NULL DEFAULT 'none',
	`until` date,
	`note` varchar(120),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `manual_blocks_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `message_receipts` (
	`message_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`delivered_at` timestamp(3) NOT NULL,
	`seen_at` timestamp(3),
	CONSTRAINT `message_receipts_message_id_user_id_pk` PRIMARY KEY(`message_id`,`user_id`)
);
--> statement-breakpoint
CREATE TABLE `messages` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`author_id` varchar(36),
	`kind` enum('text','poll','proposal','event','system') NOT NULL,
	`body` text,
	`poll_id` varchar(36),
	`proposal_id` varchar(36),
	`event_id` varchar(36),
	`seq` int AUTO_INCREMENT NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `messages_id` PRIMARY KEY(`id`),
	CONSTRAINT `messages_seq_unique` UNIQUE(`seq`)
);
--> statement-breakpoint
CREATE TABLE `notifications` (
	`id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`group_id` varchar(36),
	`kind` enum('proposal.created','proposal.confirmed','nudge','invite.accepted','message','event.created') NOT NULL,
	`title` varchar(160) NOT NULL,
	`body` varchar(400) NOT NULL,
	`data` json NOT NULL,
	`read_at` timestamp(3),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `notifications_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `poll_options` (
	`id` varchar(36) NOT NULL,
	`poll_id` varchar(36) NOT NULL,
	`label` varchar(100) NOT NULL,
	`position` int NOT NULL,
	CONSTRAINT `poll_options_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `poll_votes` (
	`option_id` varchar(36) NOT NULL,
	`poll_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `poll_votes_option_id_user_id_pk` PRIMARY KEY(`option_id`,`user_id`)
);
--> statement-breakpoint
CREATE TABLE `polls` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`created_by` varchar(36) NOT NULL,
	`question` varchar(200) NOT NULL,
	`multi` boolean NOT NULL DEFAULT false,
	`closed_at` timestamp(3),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `polls_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `proposal_responses` (
	`proposal_id` varchar(36) NOT NULL,
	`user_id` varchar(36) NOT NULL,
	`response` enum('pending','accepted','declined') NOT NULL DEFAULT 'pending',
	`set_by` varchar(36),
	`updated_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `proposal_responses_proposal_id_user_id_pk` PRIMARY KEY(`proposal_id`,`user_id`)
);
--> statement-breakpoint
CREATE TABLE `proposals` (
	`id` varchar(36) NOT NULL,
	`group_id` varchar(36) NOT NULL,
	`created_by` varchar(36) NOT NULL,
	`title` varchar(120),
	`location` varchar(160),
	`note` text,
	`start` timestamp(3) NOT NULL,
	`end` timestamp(3) NOT NULL,
	`all_day` boolean NOT NULL DEFAULT false,
	`status` enum('open','confirmed','cancelled') NOT NULL DEFAULT 'open',
	`event_id` varchar(36),
	`created_at` timestamp(3) NOT NULL DEFAULT (now()),
	CONSTRAINT `proposals_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
CREATE TABLE `school_holidays` (
	`id` varchar(36) NOT NULL,
	`country` varchar(2) NOT NULL,
	`authority` varchar(64) NOT NULL,
	`authority_name` varchar(160) NOT NULL,
	`name` varchar(120) NOT NULL,
	`start` date NOT NULL,
	`end` date NOT NULL,
	CONSTRAINT `school_holidays_id` PRIMARY KEY(`id`)
);
--> statement-breakpoint
ALTER TABLE `account` ADD CONSTRAINT `account_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `passkey` ADD CONSTRAINT `passkey_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `session` ADD CONSTRAINT `session_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `busy_intervals` ADD CONSTRAINT `busy_intervals_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `busy_intervals` ADD CONSTRAINT `busy_intervals_calendar_id_calendars_id_fk` FOREIGN KEY (`calendar_id`) REFERENCES `calendars`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `calendar_connections` ADD CONSTRAINT `calendar_connections_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `calendars` ADD CONSTRAINT `calendars_connection_id_calendar_connections_id_fk` FOREIGN KEY (`connection_id`) REFERENCES `calendar_connections`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `devices` ADD CONSTRAINT `devices_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `events` ADD CONSTRAINT `events_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `events` ADD CONSTRAINT `events_created_by_user_id_fk` FOREIGN KEY (`created_by`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_invites` ADD CONSTRAINT `group_invites_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_invites` ADD CONSTRAINT `group_invites_created_by_user_id_fk` FOREIGN KEY (`created_by`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_members` ADD CONSTRAINT `group_members_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `group_members` ADD CONSTRAINT `group_members_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `groups` ADD CONSTRAINT `groups_created_by_user_id_fk` FOREIGN KEY (`created_by`) REFERENCES `user`(`id`) ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `manual_blocks` ADD CONSTRAINT `manual_blocks_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `message_receipts` ADD CONSTRAINT `message_receipts_message_id_messages_id_fk` FOREIGN KEY (`message_id`) REFERENCES `messages`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `message_receipts` ADD CONSTRAINT `message_receipts_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `messages` ADD CONSTRAINT `messages_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `messages` ADD CONSTRAINT `messages_author_id_user_id_fk` FOREIGN KEY (`author_id`) REFERENCES `user`(`id`) ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `notifications` ADD CONSTRAINT `notifications_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `notifications` ADD CONSTRAINT `notifications_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `poll_options` ADD CONSTRAINT `poll_options_poll_id_polls_id_fk` FOREIGN KEY (`poll_id`) REFERENCES `polls`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `poll_votes` ADD CONSTRAINT `poll_votes_option_id_poll_options_id_fk` FOREIGN KEY (`option_id`) REFERENCES `poll_options`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `poll_votes` ADD CONSTRAINT `poll_votes_poll_id_polls_id_fk` FOREIGN KEY (`poll_id`) REFERENCES `polls`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `poll_votes` ADD CONSTRAINT `poll_votes_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `polls` ADD CONSTRAINT `polls_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `polls` ADD CONSTRAINT `polls_created_by_user_id_fk` FOREIGN KEY (`created_by`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `proposal_responses` ADD CONSTRAINT `proposal_responses_proposal_id_proposals_id_fk` FOREIGN KEY (`proposal_id`) REFERENCES `proposals`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `proposal_responses` ADD CONSTRAINT `proposal_responses_user_id_user_id_fk` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `proposals` ADD CONSTRAINT `proposals_group_id_groups_id_fk` FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `proposals` ADD CONSTRAINT `proposals_created_by_user_id_fk` FOREIGN KEY (`created_by`) REFERENCES `user`(`id`) ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX `account_userId_idx` ON `account` (`user_id`);--> statement-breakpoint
CREATE INDEX `passkey_userId_idx` ON `passkey` (`user_id`);--> statement-breakpoint
CREATE INDEX `passkey_credentialID_idx` ON `passkey` (`credential_id`);--> statement-breakpoint
CREATE INDEX `session_userId_idx` ON `session` (`user_id`);--> statement-breakpoint
CREATE INDEX `verification_identifier_idx` ON `verification` (`identifier`);--> statement-breakpoint
CREATE INDEX `busy_intervals_user_range_idx` ON `busy_intervals` (`user_id`,`start`,`end`);--> statement-breakpoint
CREATE INDEX `busy_intervals_calendar_idx` ON `busy_intervals` (`calendar_id`,`start`);--> statement-breakpoint
CREATE INDEX `calendar_connections_user_idx` ON `calendar_connections` (`user_id`);--> statement-breakpoint
CREATE INDEX `devices_user_idx` ON `devices` (`user_id`);--> statement-breakpoint
CREATE INDEX `events_group_idx` ON `events` (`group_id`,`start`);--> statement-breakpoint
CREATE INDEX `group_invites_group_idx` ON `group_invites` (`group_id`);--> statement-breakpoint
CREATE INDEX `group_members_user_idx` ON `group_members` (`user_id`);--> statement-breakpoint
CREATE INDEX `manual_blocks_user_idx` ON `manual_blocks` (`user_id`,`start`);--> statement-breakpoint
CREATE INDEX `message_receipts_user_idx` ON `message_receipts` (`user_id`);--> statement-breakpoint
CREATE INDEX `messages_group_seq_idx` ON `messages` (`group_id`,`seq`);--> statement-breakpoint
CREATE INDEX `notifications_user_idx` ON `notifications` (`user_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `poll_options_poll_idx` ON `poll_options` (`poll_id`);--> statement-breakpoint
CREATE INDEX `poll_votes_poll_idx` ON `poll_votes` (`poll_id`);--> statement-breakpoint
CREATE INDEX `proposals_group_idx` ON `proposals` (`group_id`,`status`);--> statement-breakpoint
CREATE INDEX `school_holidays_authority_idx` ON `school_holidays` (`country`,`authority`,`start`);