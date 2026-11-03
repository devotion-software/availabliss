-- Duplicates from the confirmation race: keep the event the proposal points at, detach the rest.
UPDATE `events` e JOIN `proposals` p ON p.`id` = e.`proposal_id` SET e.`proposal_id` = NULL WHERE p.`event_id` IS NOT NULL AND e.`id` <> p.`event_id`;--> statement-breakpoint
ALTER TABLE `events` ADD CONSTRAINT `events_proposal_unique` UNIQUE(`proposal_id`);
