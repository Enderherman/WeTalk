-- 执行前备份。先核对 SELECT version,COUNT(*) FROM app_update GROUP BY version HAVING COUNT(*)>1;
-- 有重复记录时本迁移将拒绝创建唯一索引，不会自动删除或覆盖任何发布记录。
ALTER TABLE app_update ADD UNIQUE KEY uk_app_update_version (version);

CREATE TABLE app_release_lock (
  lock_id tinyint NOT NULL,
  PRIMARY KEY (lock_id)
) ENGINE=InnoDB COMMENT='版本发布目录的事务互斥锁';
INSERT INTO app_release_lock (lock_id) VALUES (1);
