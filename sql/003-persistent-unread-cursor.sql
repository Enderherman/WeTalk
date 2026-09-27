-- Apply once to an existing WeTalk database after backing up both affected tables.
ALTER TABLE `chat_session_user`
  ADD COLUMN `last_read_message_id` int NOT NULL DEFAULT '0' COMMENT '该用户已读到的最大消息ID';

ALTER TABLE `chat_message`
  ADD KEY `idx_session_message_id` (`session_id`, `message_id`);

-- Existing messages are treated as read at rollout so deployment does not create
-- a large initial unread badge from historical chat data.
UPDATE `chat_session_user` AS `session_user`
LEFT JOIN (
  SELECT `session_id`, MAX(`message_id`) AS `latest_message_id`
  FROM `chat_message`
  GROUP BY `session_id`
) AS `latest`
  ON `latest`.`session_id` = `session_user`.`session_id`
SET `session_user`.`last_read_message_id` = COALESCE(`latest`.`latest_message_id`, 0);
