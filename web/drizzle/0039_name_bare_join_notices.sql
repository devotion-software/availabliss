-- Join notices saved before 15 Nov 2026 could read " joined the group" when the person had no name yet.
UPDATE `notifications` SET `body` = 'Someone joined the group' WHERE `kind` = 'invite.accepted' AND TRIM(`body`) = 'joined the group';
