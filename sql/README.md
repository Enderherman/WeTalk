# 数据库初始化与升级

当前默认数据库为 `wetalk`，使用 MySQL 8、InnoDB 和 utf8mb4。本目录区分全新空库初始化与已有库升级；应用启动不会自动执行这里的迁移。

## 选择正确入口

| 数据库状态 | 执行方式 |
|---|---|
| 新建且没有任何业务表 | 只导入最新 [001-schema.sql](001-schema.sql) |
| 已有旧版业务库 | 先备份并检查列/索引，再按编号顺序补齐尚未应用的 002–007 |
| 已导入最新 001 | 不再执行 002–007，它们的结构已包含在 001 中 |
| 不清楚已执行到哪里 | 先读取实际表结构和已有迁移记录，不凭应用版本猜测 |

大部分升级脚本不是幂等脚本，重复执行会报重复列、索引或表错误。004、005 的同类型扩容可重复执行，但不应据此将整套升级脚本重复运行。MySQL DDL 会隐式提交；脚本中途失败时必须检查已经完成的部分后再继续。

## 新库 001

当前 001 包含 10 张表：

| 类型 | 表 |
|---|---|
| 账号与联系人 | `user_info`、`user_info_beauty`、`user_contact`、`user_contact_apply` |
| 群和消息 | `group_info`、`chat_message`、`chat_session`、`chat_session_user` |
| 发布 | `app_update`、`app_release_lock` |

文件只包含结构与固定锁行 `app_release_lock(lock_id=1)`，自增起点归一为 1，不含业务账号、密码摘要、聊天记录、附件或安装包数据。数据库、账号及授权由环境准备；管理员和测试账号不随 SQL 自动创建。

在已经创建的空 `wetalk` 数据库中，从仓库根目录使用 MySQL 客户端执行：

```sh
mysql --defaults-extra-file=/private/mysql.cnf --database=wetalk
```

进入 MySQL 后：

```sql
SOURCE sql/001-schema.sql;
SHOW TABLES;
SELECT lock_id FROM app_release_lock;
```

凭据通过私有 defaults 文件提供，不放在命令行或 Git 中。Docker [compose.infra.yaml](../compose.infra.yaml)仅在空 MySQL 数据目录首次启动挂载并导入 001；已有目录不重新初始化。修改 `MYSQL_DATABASE` 也不会给现有数据库更名。

## 已有库 002–007

先停止会写入业务数据的应用并做可恢复备份，再逐项核对。若某项已执行，保留其记录并跳过；确认全部需要的结构就绪后再启动对应后端。

| 顺序 | 脚本与用途 | 特别注意 |
|---|---|---|
| 002 | [文字幂等键](002-client-message-idempotency.sql)：`chat_message.client_message_id varchar(36)`，发送者+键唯一索引 | 可空字段保持旧客户端兼容；仅执行一次 |
| 003 | [持久未读游标](003-persistent-unread-cursor.sql)：会话映射的 `last_read_message_id` 与消息会话索引 | 将已有会话游标推进到当时最新消息，历史消息视为已读；不能重复回填 |
| 004 | [名称扩容](004-session-contact-name.sql)：会话联系人名及消息发送者昵称改为 `varchar(40)` | 兼容合法长昵称/群名，不修改名称内容 |
| 005 | [AI 正文](005-ai-message-content.sql)：消息正文改为 `MEDIUMTEXT` | 普通输入与会话摘要的 500 字符限制仍由代码执行 |
| 006 | [私有备注](006-private-contact-remark.sql)：`user_contact.remark varchar(40)`，可空 | 只执行一次，按关系拥有者隔离 |
| 007 | [发布并发](007-release-concurrency.sql)：版本唯一索引、发布锁表及固定行 | 先排查重复版本，不能自动删除冲突记录 |

007 前检查：

```sql
SELECT version, COUNT(*)
FROM app_update
GROUP BY version
HAVING COUNT(*) > 1;
```

若有结果，先按实际发布历史处理冲突再迁移。还应留意 `1.01.0` 与 `1.1.0` 等数值相同但文本不同的旧版本；新应用会规范化版本，SQL 不替维护者决定保留哪条发布记录。

## 升级后读回

```sql
SHOW COLUMNS FROM chat_message LIKE 'client_message_id';
SHOW INDEX FROM chat_message WHERE Key_name = 'uk_chat_message_send_client';
SHOW COLUMNS FROM chat_session_user LIKE 'last_read_message_id';
SHOW INDEX FROM chat_message WHERE Key_name = 'idx_session_message_id';
SHOW COLUMNS FROM chat_session_user LIKE 'contact_name';
SHOW COLUMNS FROM chat_message LIKE 'send_user_nick_name';
SHOW COLUMNS FROM chat_message LIKE 'message_content';
SHOW COLUMNS FROM user_contact LIKE 'remark';
SHOW INDEX FROM app_update WHERE Key_name = 'uk_app_update_version';
SELECT lock_id FROM app_release_lock;
```

预期依次为可空 `varchar(36)` 与组合唯一索引、整型已读游标与会话消息索引、两个 `varchar(40)`、`mediumtext`、可空 `varchar(40)`、版本唯一索引及唯一锁行 1。再验证实际注册、消息、长昵称、备注和版本发布，不能只以 SQL 未报错代替业务验收。

## 维护脚本

[export_schema.py](../scripts/export_schema.py)从指定数据库导出当前约定的 10 张表结构，移除自增历史计数，并补固定锁行：

```sh
python scripts/export_schema.py --defaults-file /private/mysql.cnf --database wetalk
```

默认覆盖 `sql/001-schema.sql`；导出前确认源库已完成全部迁移，之后审阅 diff。脚本不导出业务行、触发器、账号或授权。

[rename_database.py](../scripts/rename_database.py)只用于已有数据库需要更名的情况，默认从 `easychat` 到 `wetalk`，先运行只读预检：

```sh
python scripts/rename_database.py --defaults-file /private/mysql.cnf
```

确认计划后增加 `--apply` 才会备份和跨库移动表，并保存行数、CHECKSUM 与回退 SQL 到 Git 忽略的 `.private/db-backups`。当前工具直接复用导出脚本的 **10 张表集合**；仅有旧 9 张表的库不能直接使用。工具还要求 MySQL 8、完整 InnoDB 表集合、空目标库、无相关触发器/存储过程/事件且旧库无其他连接。它不会更新数据库账号授权或应用 `DB_URL`，也不会移动附件。

迁移是部署操作，本次文档重写未连接或修改生产库。历史验证及 NAS 状态见 [CHANGELOG](../CHANGELOG.md)，运行配置见 [README](../README.md)。
