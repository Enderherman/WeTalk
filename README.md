# WeTalk 后端（0.0.2）

WeTalk 是 App 和 Web 共用的 Java 17 / Spring Boot 聊天服务。后端提供账号、联系人、群聊、消息历史、文件和管理员接口；MySQL 保存业务数据，Redis 保存会话/缓存并由 Redisson 广播消息，Netty 推送 WebSocket。AI 可选，默认关闭。

## 当前地址与协议

| 项目 | 默认地址/行为 |
|---|---|
| HTTP API | http://主机:5050/api |
| WebSocket | Web: ws://主机:5051/ws?ticket=短时票据；Electron 兼容旧 token 参数 |
| 就绪检查 | GET /api/actuator/health/readiness |
| 健康检查 | GET /api/actuator/health |
| 登录态 | Web 使用 HttpOnly Cookie；Electron 兼容 token 请求头；失败业务码 901 |
| 头像/封面图片 | 用户资料和机器人头像接受 PNG、JPEG、GIF、BMP、WebP；扩展名、MIME 与文件签名需匹配，每个文件最多 10 MiB |
| 返回值 | status、code、message、data；客户端按业务 code 判断结果 |
| 账号注册 | 邮箱、密码、昵称、图片验证码；目前没有邮箱归属验证 |
| AI | 默认不启用，无 AI 密钥也能启动普通聊天 |

生产反向代理应使用 HTTPS/WSS，并保持 /api、/ws 两个路径；HTTPS 部署设 WETALK_WEB_AUTH_COOKIE_SECURE=true。健康接口仅返回 UP/DOWN，不公开配置或数据库详情；就绪检查同时检查 MySQL、Redis 和 WebSocket 是否成功绑定。

## 本次修复

详见 [CHANGELOG.md](CHANGELOG.md)。重点修复了私聊附件越权、群解散误断开连接、离线同步时间、过期连接清理、Redis 密码/数据库配置、旧 token 残留和首次部署配置；群成员配额改为读取正确的 `maxGroupMemberCount`。机器人和用户头像/封面上传都会校验格式、文件签名和 10 MiB 上限。同时启用 Jakarta 参数校验，管理员预留邮箱不能经公开注册获得权限，API 返回的用户对象不再含密码摘要。

管理员账号须由可信的数据库初始化或现有账号配置完成，ADMIN_EMAILS 只填写已核实身份的邮箱；不能依靠公开注册创建管理员。已有配置曾包含真实连接信息时，需要在实际服务上更换相应凭据，当前文件改为环境变量不会清理 Git 历史。

## 运行条件

- JDK 17 或兼容的更新 JDK、Maven 3.9+。
- 可访问的 MySQL（数据库已建好，已有 WeTalk 表结构和所需初始数据）。
- 可访问的 Redis。Spring Redis 和 Redisson 共用 host、port、password、username、database、TLS 设置。
- 可写的文件目录，重启容器时必须保留。
- AI 使用 OpenAI 兼容接口时，还需要相应 provider/model/API Key。

sql/001-schema.sql 已从本机实际使用的 MySQL 8.0.31 wetalk 导出，包含 9 张业务表的字段、索引和字符集，不含用户、密码摘要、聊天记录等私有数据。它只用于空数据库初始化，不是已有数据库升级脚本；参见 [SQL 说明](sql/README.md)。演示账号和管理员账号另行创建，旧数据库/文件的数据迁移需单独备份处理。

本项目没有 Maven Wrapper，下面使用安装好的 mvn。

## 数据库统一命名为 wetalk

后端默认 JDBC 地址、Docker 首次初始化、MySQL 健康检查、环境模板和准备/导出脚本均使用 wetalk。已有环境的 DB_URL 也必须指向 /wetalk。

本机已有的 easychat 数据库采用一次 RENAME TABLE 将 9 张 InnoDB 业务表整体迁到 wetalk，原有记录和索引保持。迁移前保留全量私有备份，迁移后逐表核对行数和 CHECKSUM；旧 easychat 保留为空 schema，便于回退，没有 DROP DATABASE。MySQL 的[跨库重命名说明](https://dev.mysql.com/doc/refman/8.0/en/rename-table.html)解释了该操作和触发器等限制。

对其他已经运行的旧环境，先停止后端并关闭旧库连接；脚本默认只检查，--apply 才执行：

~~~shell
python scripts/rename_database.py --defaults-file /private/mysql.cnf
python scripts/rename_database.py --defaults-file /private/mysql.cnf --apply
~~~

脚本只处理已知 9 张 InnoDB 表，拒绝有对象的目标库及触发器/存储过程/事件等复杂场景；会把备份与回退 SQL 放入 Git 忽略的 .private/db-backups。默认旧库 easychat、新库 wetalk；不改变账号授权。若原数据库账号只授权 easychat.*，须由管理员重新授予 wetalk 的相应最小权限。

对已初始化的 MySQL Docker 数据目录，仅修改 MYSQL_DATABASE 环境变量不会更名数据库；不要删除数据目录来重跑初始化。新部署则由 compose.infra.yaml 创建 wetalk 并导入空表结构。NAS 当前部署仍暂停，先前传到 NAS 的构建包恢复部署时需要替换为本次新包。

## 本地构建与测试

~~~shell
mvn clean verify
java -jar target/wetalk.jar
~~~

构建产物为 target/wetalk.jar（可执行 Spring Boot JAR）。不需要在构建机连接真实 MySQL/Redis：现有测试使用隔离替身和 Spring 小范围上下文，覆盖附件权限、参数校验、会话撤销、连接生命周期、启动失败和 AI 禁用配置。

~~~shell
python scripts/package_release.py
~~~

这会生成 dist/wetalk-backend-0.0.2-nas.zip 和对应 SHA256。它包含 JAR、Docker 构建文件、Compose、环境模板、文档、测试统计和内部文件校验清单；不包含真实 .env、数据目录、Git 历史或数据库备份。

## 环境变量

Spring Boot 不会自动读取本地 .env；本地直接运行 Java 时要由终端导出环境变量，Docker Compose 才会通过 env_file 读取它。

| 变量 | 默认/要求 |
|---|---|
| DB_URL | 默认 localhost:3306/wetalk；NAS 上必须填真实 JDBC 地址 |
| DB_USERNAME / DB_PASSWORD | 专用数据库账号和密码；默认 wetalk / 空密码只作本地占位 |
| DB_POOL_SIZE | 10 |
| REDIS_HOST / REDIS_PORT | 127.0.0.1 / 6379；容器内 localhost 指容器本身 |
| REDIS_DATABASE | 0；复用现有 Redis 时选择合适的数据库，避免与其他 WeTalk 实例共用键 |
| REDIS_USERNAME / REDIS_PASSWORD | 可选 ACL 用户和 Redis 密码 |
| REDIS_SSL | false；启用时 Spring 和 Redisson 都使用 TLS |
| PROJECT_FOLDER | 本地 ./data/；Docker /data/wetalk/ |
| WETALK_WEB_AUTH_COOKIE_SECURE | false 本地开发；HTTPS 部署必须设为 true，启用 HttpOnly/SameSite Strict 的 Web 会话 Cookie |
| HTTP_PORT / WS_PORT | 5050 / 5051；Compose 固定内部端口 |
| ADMIN_EMAILS | 默认为空；可信的现有管理员邮箱，以逗号分隔 |
| MAX_UPLOAD_SIZE | 500MB，HTTP 与文件请求上限 |
| SPRING_PROFILES_ACTIVE | dev；Compose 设为 docker |
| WETALK_AI_ENABLED | false |
| WETALK_AI_MODEL | none；启用 AI 时设 openai |
| OPENAI_BASE_URL / OPENAI_MODEL / OPENAI_API_KEY | AI 启用时按提供商填写 |

启用 AI 需同时设置 WETALK_AI_ENABLED=true 和 WETALK_AI_MODEL=openai，再填写 model、base URL、API Key。普通聊天部署保持 false/none。此行为通过 Spring AI 自动配置测试验证，不使用伪造 API Key。


### 网页 AI 流式回复与停止

AI 默认关闭。启用时设置 `WETALK_AI_ENABLED=true`、`WETALK_AI_MODEL=openai`，并填写 OpenAI 兼容提供方的 base URL、模型名和 API Key。Web 客户端向机器人 `Urobot` 发送问题后，WebSocket 类型 14/15/16 分别表示初始化、累计全文片段和结束；结束状态 1 为完成、2 为停止、3 为提供方失败。

`POST /api/chat/cancelAiMessage` 接受正整数 `messageId`，只允许停止当前登录用户发起的 AI 回复。停止会取消提供方流并保存已生成文本；提供方错误也会保存已有文本并写入失败状态。服务器重启后遗留的空 AI 占位消息会在用户尝试停止时标为失败，避免永久等待。完整请求契约见 `WeTalkWeb/docs/openapi.web.json`。
## NAS Docker 部署准备

### 独立 MySQL / Redis 基础容器

compose.infra.yaml 用官方 MySQL 8.4、Redis 7.4 创建两个独立容器，分别名为 wetalk-mysql 和 wetalk-redis。它们共用 wetalk-net 网络，数据保存在该专用目录的 data/mysql 和 data/redis。

准备脚本会随机生成独立的 MySQL root 密码、应用数据库密码和 Redis 密码；密码保存在 .env、secrets/ 和 config/redis.conf，不会打印或提交到 Git。MySQL root 限制为本机登录，应用账号 wetalk 仅用于 wetalk 数据库。默认发布地址是 127.0.0.1，需要局域网开发时显式指定 NAS 的 LAN IP。

~~~shell
cd /volume2/docker/wetalk
python3 scripts/prepare_infra.py --bind-ip 192.168.31.108
sudo chown 999:999 config/redis.conf
sudo docker compose -f compose.infra.yaml config --quiet
sudo docker compose -f compose.infra.yaml up -d
sudo docker compose -f compose.infra.yaml ps
~~~

默认 NAS 侧端口为 MySQL 13306、Redis 16379，以避开现有服务；实际启动前仍需核对端口。两者启用认证和持久化，MySQL 首次启动自动导入 sql/001-schema.sql。不要通过清空 data/mysql 重跑初始化；已有数据目录的升级需专门迁移脚本。

生成配置拒绝覆盖已有 .env/secrets/config，避免意外轮换凭据。Redis 配置文件必须由容器 UID/GID 999 读取；父目录和 .env 保持私有权限。同一网络中的后端用 wetalk-mysql:3306、wetalk-redis:6379；从本机开发工具连接 NAS 用 192.168.31.108:13306 / 16379。

本次只要求创建两个基础容器。后端镜像可以先构建，之后确认时再用 compose.yaml + compose.nas.yaml 接入：

~~~shell
sudo docker build -t wetalk-backend:0.0.2 .
# 后续启动后端时才执行：
sudo docker compose -f compose.yaml -f compose.nas.yaml up -d
~~~

prepare_infra.py 为后续后端预留 NAS 发布端口 15050/15051，避免碰本机已有 5050/5051 服务；它不会自行启动第三个容器。

### 单独后端容器

这里是下一步部署操作说明，本次打包不等于已部署到 NAS。

1. 核对 NAS 架构、端口占用、已有 MySQL/Redis 地址、schema 和数据备份。建议独立目录 /volume2/docker/wetalk；这是建议路径，实际需现场核对。
2. 将发布 ZIP 传到 NAS，解压后核验文件校验和；包内保留 target/wetalk.jar 的相对结构。
3. 将 .env.example 复制为 .env 并填写真实连接参数。Compose 仅启动 WeTalk，不会改动或新建其他 MySQL、Redis 服务。
4. 创建持久化文件目录，让容器 UID/GID 10001 有读写权限。
5. 构建和启动，等待健康检查成功，再验证真实注册、登录、聊天、下载和 WebSocket。

~~~shell
cd /volume2/docker/wetalk
sha256sum -c SHA256SUMS
cp .env.example .env
# 编辑 .env，完成 DB/Redis/管理员等配置
mkdir -p data
# 使用 NAS 可用的管理员方式设置该专用目录权限
chown 10001:10001 data
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 wetalk
curl -f http://127.0.0.1:5050/api/actuator/health/readiness
~~~

Compose 映射 5050 和 5051，可用 HTTP_PUBLISHED_PORT / WS_PUBLISHED_PORT 修改 NAS 侧端口。WETALK_DATA_DIR 控制持久化目录，建议采用经确认的绝对路径。容器非 root 运行，根文件系统只读，临时上传写 /tmp，正式文件写挂载目录。

Dockerfile 使用官方 [Eclipse Temurin](https://hub.docker.com/_/eclipse-temurin) Java 17 JRE 镜像，安装验证码所需字体和健康检查工具。构建需要网络获取基础镜像/系统包；如果 NAS 拉取受阻，可在有 Linux Docker 引擎的机器构建同架构镜像、docker save、校验传输后 docker load。当前发布 ZIP 是构建包，**不是 docker load 可导入的镜像 tar**。

## App / Web 连接 NAS

- Electron App 的 prodDomain 使用 http://NAS地址:5050（不附加 /api），prodWsDomain 使用 ws://NAS地址:5051/ws。当前客户端默认 localhost，部署后需改为真实地址并重新打包；本次没有改 App。
- 当前 WeTalkWeb 默认相对 /api，WebSocket 使用页面同源 /ws。开发时在 WeTalkWeb/vite.config.ts 将 /api 和 /ws 的代理目标改为 NAS HTTP/WS 地址。
- Web 的 VITE_API_BASE_URL 可配置 REST 基址，但 WebSocket 同源策略仍需 /ws 代理。生产建议反向代理统一域名，Web 静态页面、API 和 WebSocket 共用 HTTPS/WSS。
- 本后端没有添加任意 Origin 的全局 CORS 放行；用同源代理即可保留权限边界。
- 消息通过 POST /api/chat/sendMessage 发送，通过 WebSocket 接收。历史分页接口为 POST /api/chat/loadHistory，参数 contactId、beforeMessageId、pageSize（1..50）。
- 默认单账号单活跃连接；本次没有实现 App 与 Web 同账号同时在线。开发联调应使用两个不同测试账号，后续多端会话需要专门设计。

## 部署后必须验证

健康检查只验证依赖和端口，实际业务还须覆盖：
注册/图片验证码、登录、退出后复用旧 token 被拒绝、双账号互发消息、重连初始同步、私聊附件第三方下载被拒绝、群解散后其他会话仍在线，以及持久化文件重启后可读。启用 AI 时单独验证真实提供商的流式回答。

## 已知后续工作

- 当前密码协议仍兼容 App/Web 的历史 MD5 登录摘要；升级 BCrypt/Argon2 和邮箱验证必须协调客户端及已有账号迁移。
- 管理员角色仍按可信邮箱名单配置，后续宜加入数据库角色和邮箱验证。
- `/api/app/downloadUpdate` 已实现为受登录态保护的本地更新包下载端点；仅允许全量发布或当前账号在灰度名单中的包。外链更新仍由客户端打开 `outerLink`。
- SQL 初始化不含本机历史数据；MySQL/Redis 容器部署结果和 App/Web 端到端验证需记录实际结果。
