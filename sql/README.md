# 初始化 SQL

001-schema.sql 来自本机后端实际使用的 MySQL 8.0.31 easychat 数据库，以 mysqldump --no-data 导出以下 9 张表：

app_update、chat_message、chat_session、chat_session_user、group_info、user_contact、user_contact_apply、user_info、user_info_beauty。

保留原始字段、索引、字符集和排序规则；自增计数重置为 1。没有导出任何业务记录、账号、密码摘要、聊天内容或更新包路径。本机该数据库没有触发器。

仅用于新建空数据库。不能作为已有数据库的升级脚本，也不会迁移本机历史数据。官方 MySQL 容器只在空数据目录的首次启动执行 docker-entrypoint-initdb.d 中的 SQL。

管理员账号、演示账号和测试联系人需后续明确初始化；注册流程会使用代码中的默认系统配置创建机器人联系人和欢迎消息。

重新导出时，通过私有的 MySQL defaults 文件提供凭据：

~~~shell
python scripts/export_schema.py --defaults-file /private/mysql-export.cnf --database easychat
~~~

defaults 文件必须保存在 Git 忽略的私有目录，不能把密码写在命令参数或提交到 Git。
