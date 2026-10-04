# 初始化 SQL

001-schema.sql 来自本机后端实际使用的 MySQL 8.0.31 wetalk 数据库，以 mysqldump --no-data 导出以下 9 张表：

app_update、chat_message、chat_session、chat_session_user、group_info、user_contact、user_contact_apply、user_info、user_info_beauty。

保留原始字段、索引、字符集和排序规则；自增计数重置为 1。没有导出任何业务记录、账号、密码摘要、聊天内容或更新包路径。本机该数据库没有触发器。

仅用于新建空数据库。不能作为已有数据库的升级脚本，也不会迁移本机历史数据。官方 MySQL 容器只在空数据目录的首次启动执行 docker-entrypoint-initdb.d 中的 SQL。

已有数据库升级使用单独的顺序迁移脚本，不要重复执行：

- `002-client-message-idempotency.sql` 为 `chat_message` 增加可空客户端幂等键及发送者组合唯一索引；旧桌面客户端不传该字段时仍可写入多条 `NULL`。
- `003-persistent-unread-cursor.sql` 为每个用户/会话映射保存已读消息游标，并添加未读统计索引；迁移时把既有会话历史标为已读，避免升级后突然出现历史未读数。
- `004-session-contact-name.sql` 将 `chat_session_user.contact_name` 和 `chat_message.send_user_nick_name` 扩容到 40 字符，与用户昵称上限一致，兼容 32 字符群名。不会修改名称、消息或已读游标；该扩容脚本可重复执行。应用后使用 `SHOW COLUMNS FROM chat_session_user LIKE 'contact_name'` 和 `SHOW COLUMNS FROM chat_message LIKE 'send_user_nick_name'` 确认均为 `varchar(40)`。
- `005-ai-message-content.sql` 将 `chat_message.message_content` 扩为 MEDIUMTEXT，完整保存长 AI 回复；普通输入限制和会话摘要上限仍为 500 字符。使用 `SHOW COLUMNS FROM chat_message LIKE 'message_content'` 确认类型为 `mediumtext`。
- 应先备份数据库，再手工应用迁移并验证列/索引存在，然后部署需要该字段的后端版本。

2026-10-04：004 已随源码提供，本轮没有连接或迁移运行中的数据库；部署验收必须另外记录真实迁移与长昵称收发、长群名创建/修改的结果。

管理员账号、演示账号和测试联系人需后续明确初始化；注册流程会使用代码中的默认系统配置创建机器人联系人和欢迎消息。

重新导出时，通过私有的 MySQL defaults 文件提供凭据：

~~~shell
python scripts/export_schema.py --defaults-file /private/mysql-export.cnf --database wetalk
~~~

defaults 文件必须保存在 Git 忽略的私有目录，不能把密码写在命令参数或提交到 Git。
