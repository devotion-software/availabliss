DROP INDEX `school_holidays_authority_idx` ON `school_holidays`;--> statement-breakpoint
CREATE INDEX `school_holidays_authority_idx` ON `school_holidays` (`authority`,`start`);