# 私有好友备注

2026-10-04。备注属于当前账号与好友之间的关系，不修改对方昵称、消息发送者昵称或群名称。

## 保存与清空

`POST /api/contact/saveRemark`，使用现有 Cookie 或 token 登录态，表单字段：

| 字段 | 规则 |
|---|---|
| `contactId` | 当前双向有效好友的用户编号；不接受自己、群聊或陌生人 |
| `remark` | 去掉首尾空格后最多 40 个 UTF-16 代码单元；空串清除备注；参数不能省略 |

成功返回 `BaseResponse`，`data` 为 `{"contactId":"U...","remark":"备注"}`；清空时 `remark` 为 `""`。仅用登录账号作为修改者，不接受客户端指定 `userId`。任一方向已经删除/拉黑、对方禁用/删除、超长备注或不合法联系人均拒绝。

## 读取与多设备同步

- `loadContact` 的好友记录增加 `remark`；`getContactInfo`、`getContactUserInfo` 增加 `remark`，`nickName` 仍为真实昵称。
- `search`/`searchByKeyword` 的用户结果增加当前账号的 `remark`，陌生账号看不到他人的备注；全站搜索仍按邮箱/编号/昵称，不检索其他人的私有备注。
- WebSocket INIT 的会话记录增加 `remark`，`contactName` 保持真实联系人名称。客户端可显示 `remark || contactName`，搜索/资料页可使用 `remark || nickName`。
- 保存事务提交后向该账号所有在线设备发送 type **18**。这是资料同步事件，不是聊天消息，不计入未读，也不插入消息历史。
- type 18 的外层 `contactId` 是备注拥有者；`extentData` 为 `{"contactId":"被备注好友编号","remark":"新备注或空串"}`。其他账号不会接收该事件。

## 数据库

`user_contact.remark` 为 `varchar(40)`，默认 NULL，复合主键 `(user_id, contact_id)` 隔离拥有者。保存只更新自己的关系行；删除后恢复好友关系保留该行上的备注。新数据库使用最新 001；已有数据库备份后执行一次 `sql/006-private-contact-remark.sql`。本次提交没有迁移运行中的数据库。

## 验证

自动化覆盖：保存/清空/长度边界、自己/群/陌生人拒绝、任一方向删除或拉黑拒绝、并发关系变化导致写入失败时不广播、事务提交后广播及回滚不广播、详情/搜索保留真实昵称、控制器使用登录身份、仅本人两设备收到 type 18、INIT 同时携带真实名称与私有备注。真实 SQL 与 HTTP/WebSocket 联调由隔离集成验收补充。
