-- 执行前备份数据库。AI 回复可能超过普通输入的 500 字符上限，正文必须完整保存。
-- 会话列表仍存 500 字符内的摘要，由服务端安全裁剪，不能用摘要替代正文。
ALTER TABLE chat_message
  MODIFY COLUMN message_content mediumtext COMMENT '消息内容';
