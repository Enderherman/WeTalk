# 初始化 SQL

001-schema.sql 基于本机 MySQL 8.0.31 wetalk 导出的结构维护，当前包含 9 张业务表和 1 张发布事务锁表：

app_update、chat_message、chat_session、chat_session_user、group_info、user_contact、user_contact_apply、user_info、user_info_beauty，以及 app_release_lock。

结构随迁移同步；自增计数重置为 1。只包含发布锁表固定 `(lock_id=1)` 基础行，没有任何业务记录、账号、密码摘要、聊天内容或更新包路径。本机原数据库没有触发器。

仅用于新建空数据库。不能作为已有数据库的升级脚本，也不会迁移本机历史数据。官方 MySQL 容器只在空数据目录的首次启动执行 docker-entrypoint-initdb.d 中的 SQL。

已有数据库升级使用单独的顺序迁移脚本，不要重复执行：

- `002-client-message-idempotency.sql` 为 `chat_message` 增加可空客户端幂等键及发送者组合唯一索引；旧桌面客户端不传该字段时仍可写入多条 `NULL`。
- `003-persistent-unread-cursor.sql` 为每个用户/会话映射保存已读消息游标，并添加未读统计索引；迁移时把既有会话历史标为已读，避免升级后突然出现历史未读数。
- `004-session-contact-name.sql` 将 `chat_session_user.contact_name` 和 `chat_message.send_user_nick_name` 扩容到 40 字符，与用户昵称上限一致，兼容 32 字符群名。不会修改名称、消息或已读游标；该扩容脚本可重复执行。应用后使用 `SHOW COLUMNS FROM chat_session_user LIKE 'contact_name'` 和 `SHOW COLUMNS FROM chat_message LIKE 'send_user_nick_name'` 确认均为 `varchar(40)`。
- `005-ai-message-content.sql` 将 `chat_message.message_content` 扩为 MEDIUMTEXT，完整保存长 AI 回复；普通输入限制和会话摘要上限仍为 500 字符。使用 `SHOW COLUMNS FROM chat_message LIKE 'message_content'` 确认类型为 `mediumtext`。
- `006-private-contact-remark.sql` 为 `user_contact` 增加可空 `remark varchar(40)`，按当前账号与联系人复合主键保存私有备注。使用 `SHOW COLUMNS FROM user_contact LIKE 'remark'` 核对类型；已有库仅执行一次，新库的最新 001 已包含该列。
- `007-release-concurrency.sql` 为 `app_update.version` 添加唯一索引，并新增 `app_release_lock` 及固定行。先用 `SELECT version,COUNT(*) FROM app_update GROUP BY version HAVING COUNT(*)>1` 检查重复版本；有重复时不要直接部署新后端，按实际发布记录人工处理后再迁移。脚本不会自动删记录。应用后核对 `SHOW INDEX FROM app_update` 与 `SELECT lock_id FROM app_release_lock` 返回 1。
- 应先备份数据库，再手工应用迁移并验证列/索引存在，然后部署需要该字段的后端版本。

2026-10-04：004 已随源码提供，本轮没有连接或迁移运行中的数据库；部署验收必须另外记录真实迁移与长昵称收发、长群名创建/修改的结果。

管理员账号、演示账号和测试联系人需后续明确初始化；注册流程会使用代码中的默认系统配置创建机器人联系人和欢迎消息。

重新导出时，通过私有的 MySQL defaults 文件提供凭据：

~~~shell
python scripts/export_schema.py --defaults-file /private/mysql-export.cnf --database wetalk
~~~

defaults 文件必须保存在 Git 忽略的私有目录，不能把密码写在命令参数或提交到 Git。
