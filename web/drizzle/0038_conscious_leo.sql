-- TIMESTAMP → DATETIME converts through the session zone: pin it to UTC so stored instants don't shift.
SET time_zone = '+00:00';--> statement-breakpoint
ALTER TABLE `events` MODIFY COLUMN `start` datetime(3) NOT NULL;--> statement-breakpoint
ALTER TABLE `events` MODIFY COLUMN `end` datetime(3) NOT NULL;--> statement-breakpoint
ALTER TABLE `manual_blocks` MODIFY COLUMN `start` datetime(3) NOT NULL;--> statement-breakpoint
ALTER TABLE `manual_blocks` MODIFY COLUMN `end` datetime(3) NOT NULL;--> statement-breakpoint
ALTER TABLE `proposals` MODIFY COLUMN `start` datetime(3) NOT NULL;--> statement-breakpoint
ALTER TABLE `proposals` MODIFY COLUMN `end` datetime(3) NOT NULL;