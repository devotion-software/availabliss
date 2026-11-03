ALTER TABLE `calendar_connections` ADD `account_label` varchar(255) GENERATED ALWAYS AS (if(provider = 'device', null, label)) STORED;--> statement-breakpoint
-- Racing connects could leave the same account connected twice: keep the most recently updated one.
DELETE `c` FROM `calendar_connections` `c`
JOIN `calendar_connections` `k`
  ON `k`.`user_id` = `c`.`user_id` AND `k`.`provider` = `c`.`provider` AND `k`.`account_label` = `c`.`account_label`
 AND (`k`.`updated_at` > `c`.`updated_at` OR (`k`.`updated_at` = `c`.`updated_at` AND `k`.`id` > `c`.`id`));--> statement-breakpoint
ALTER TABLE `calendar_connections` ADD CONSTRAINT `calendar_connections_account_uq` UNIQUE(`user_id`,`provider`,`account_label`);
