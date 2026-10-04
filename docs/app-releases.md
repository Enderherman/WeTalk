# 版本发布管理

本契约适用于管理员维护桌面更新记录，以及已登录客户端检查和下载更新。所有路径带 `/api/app` 前缀；管理操作要求管理员身份，客户端可见性使用当前登录账号。更新历史见 [CHANGELOG](../CHANGELOG.md)。

## 接口与状态

客户端使用 POST 表单请求；上传本地安装包使用 multipart。

| 路径 | 用途与主要参数 |
|---|---|
| `/loadUpdateList` | 管理员分页查询，按 ID 倒序 |
| `/saveUpdate` | 新建或编辑草稿；`id` 可选，必填 `version`、`updateDesc`、`fileType`，另按类型提供 `file` 或 `outerLink` |
| `/postUpdate` | 更改发布状态；必填 `id`、`status`，灰度时填写 `grayscaleUid` |
| `/deleteUpdate` | 仅删除草稿记录，必填 `id` |
| `/checkUpdate` | 登录用户检查高于 `version` 的最高可见版本；兼容接收旧 `uid` 参数，但不用于鉴权 |
| `/downloadUpdate` | 登录用户按 `id` 下载对本人可见的本地安装包 |

`status=0` 为未发布，`1` 为灰度发布，`2` 为全量发布。编辑或删除前必须撤回为 0；不存在的 ID 返回业务错误。撤回后客户端不再得到该发布记录。删除草稿当前只删除数据库记录，不主动清理磁盘安装包。

## 输入规则

- 版本必须是三段数字，原始长度最多 10 字符，例如 `1.10.0`。保存时去掉数字前导零，比较按 major/minor/patch 数值执行。新建或编辑后的版本必须大于其他有效历史记录的版本；不支持预发布标签。
- 更新说明非空，最多 500 个 UTF-16 代码单元。
- `fileType=0` 为本地安装包。新建需非空 `.exe`，最大 500 MiB；编辑已有本地草稿且原包存在时可省略新文件。
- `fileType=1` 为外链。URL 最多 200 字符，只允许有主机名且不含用户名/密码的 HTTP/HTTPS 地址；不能同时上传文件。
- 灰度名单使用 `U` 加 11 位数字的用户编号，可用逗号、中文逗号或空白分隔；去重后最多 1000 字符。撤回或全量发布清空灰度名单。
- 发布本地记录前重新检查安装包存在且非空；外链记录重新验证 URL。

HTTP multipart 同时受 `MAX_UPLOAD_SIZE` 的单文件与整请求上限约束；需要预留表单开销，不能仅按安装包逻辑上限配置反向代理。

## 并发与文件保存

所有发布写操作先在事务内锁定 `app_release_lock(lock_id=1)`，再读取当前版本与状态。草稿编辑/删除及发布状态变更使用带条件的 SQL；状态已改变时要求刷新重试。缺少锁行时写操作失败关闭，查询与下载不占该写锁。

`app_update.version` 有唯一索引。已有库须先检查重复版本并应用 [007 迁移](../sql/007-release-concurrency.sql)；迁移遇到冲突会失败，不会自行删除记录。新库已在 [001](../sql/001-schema.sql)内包含这些结构。

上传先完整写入临时文件，再更新记录并移动到 `PROJECT_FOLDER/file/app/<id>.exe`。传输失败保留原记录与原包，并清理暂存文件；数据库写操作在异常时回滚。磁盘文件和数据库不是分布式事务，发布与备份时应同时核对记录和实际文件。

## 更新可见性

客户端只看到全量版本或包含当前登录账号的灰度版本。草稿、其他账号的灰度版本和不高于客户端版本的记录不会返回；没有更新时 `data` 为空。本地包下载再次验证相同可见性，并拒绝把外链记录当作本地文件读取。

版本提示与安装包下载是两种客户端行为；网页可显示版本说明，不代表浏览器能够安装 Windows 包。

## 维护与验证入口

- [AppUpdateController](../src/main/java/top/enderherman/wetalk/controller/AppUpdateController.java)：接口与身份。
- [AppUpdateServiceImpl](../src/main/java/top/enderherman/wetalk/service/impl/AppUpdateServiceImpl.java)：输入、状态、锁与文件。
- [AppVersion](../src/main/java/top/enderherman/wetalk/utils/AppVersion.java)：版本规范化与比较。
- [AppReleaseManagementTest](../src/test/java/top/enderherman/wetalk/service/AppReleaseManagementTest.java)、[AppUpdateDownloadTest](../src/test/java/top/enderherman/wetalk/service/AppUpdateDownloadTest.java)：发布状态、并发条件、上传失败和下载权限回归。

数据库升级步骤见 [SQL 说明](../sql/README.md)，功能基线与本轮未部署边界见 [README](../README.md)。
