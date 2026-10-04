-- 对已有数据库执行一次；先备份并核对列不存在。新数据库直接使用最新 001。
ALTER TABLE user_contact
  ADD COLUMN remark varchar(40) DEFAULT NULL COMMENT '当前用户的私有好友备注' AFTER contact_type;
