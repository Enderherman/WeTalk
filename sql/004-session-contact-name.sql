-- 执行前备份数据库。扩容不会修改现有名称或已读游标，可重复执行。
-- 用户昵称最多 40 字符、群名最多 32 字符，会话冗余名称必须容纳二者。
ALTER TABLE chat_session_user
  MODIFY COLUMN contact_name varchar(40) DEFAULT NULL COMMENT '联系人名称';

ALTER TABLE chat_message
  MODIFY COLUMN send_user_nick_name varchar(40) DEFAULT NULL COMMENT '发送人昵称';
