-- Apply once to an existing WeTalk database before deploying a backend build
-- that accepts clientMessageId. NULL keys preserve compatibility with old clients.
ALTER TABLE `chat_message`
  ADD COLUMN `client_message_id` varchar(36) DEFAULT NULL COMMENT '客户端消息幂等键' AFTER `message_id`,
  ADD UNIQUE KEY `uk_chat_message_send_client` (`send_user_id`, `client_message_id`);
