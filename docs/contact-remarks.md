# 私有好友备注

备注属于当前账号的一条好友关系，只影响本人展示名称。它不修改好友真实昵称、消息发送者昵称或群名称，也不会向好友公开。

## 保存与清空

使用 Cookie 或 token 登录态调用 `POST /api/contact/saveRemark`，提交表单：

| 字段 | 规则 |
|---|---|
| `contactId` | 双向关系均有效的好友用户编号；拒绝自己、群、陌生人及不可用账号 |
| `remark` | 必填；去掉首尾空格后最多 40 个 UTF-16 代码单元；空字符串清空 |

任一方向删除或拉黑后均不能保存备注。服务端从登录态取得修改者，不接受客户端指定其他 `userId`。成功响应的 `data` 为：

```json
{
  "contactId": "U12345678901",
  "remark": "同事"
}
```

清空成功时 `remark` 返回 `""`。这是响应形状示例，不是真实账号资料。

## 读取与展示

| 返回位置 | 真实名称 | 私有字段 |
|---|---|---|
| 好友列表 `loadContact` | 原联系人名称 | `remark` |
| 资料 `getContactInfo` / `getContactUserInfo` | `nickName` | `remark` |
| 搜索 `search` / `searchByKeyword` | `nickName` | 当前账号自己的 `remark` |
| WebSocket INIT 会话 | `contactName` | `remark` |

客户端展示可优先选择备注，未设置时回退真实昵称。不能把备注写回 `nickName` 或 `contactName`；搜索服务仍按邮箱、编号、昵称等公开字段查找，不检索其他账号的私有备注。

## 本人设备同步

保存事务提交后向该账号的在线设备发送 type **18**：

- 外层 `contactId` 为备注拥有者。
- `extentData.contactId` 为被备注好友编号。
- `extentData.remark` 为新备注或空字符串。

客户端应通过 `extentData.contactId` 更新好友与会话展示。type 18 是资料事件，不生成聊天记录、不增加未读，也不额外转发给对端。断线后由新的资料请求或 INIT 恢复当前值。

## 数据库与关系恢复

`user_contact.remark` 为可空 `varchar(40)`，复合主键 `(user_id, contact_id)` 区分拥有者。保存只更新本人的关系行；删除后重新恢复该好友关系时保留原行备注。

新库使用完整 [001](../sql/001-schema.sql)；已有库仅在缺少该列时备份并执行一次 [006](../sql/006-private-contact-remark.sql)。迁移与读回步骤见 [SQL 说明](../sql/README.md)。

## 维护与验证入口

[UserContactController](../src/main/java/top/enderherman/wetalk/controller/UserContactController.java)确定登录身份，[UserContactServiceImpl](../src/main/java/top/enderherman/wetalk/service/impl/UserContactServiceImpl.java)实现校验、保存与提交后通知。

[ContactRemarkTest](../src/test/java/top/enderherman/wetalk/service/ContactRemarkTest.java)覆盖保存/清空、长度、关系、事务、真实昵称与本人设备隔离；[SameAccountFanoutTest](../src/test/java/top/enderherman/wetalk/webSocket/SameAccountFanoutTest.java)防止控制事件被当作私聊消息镜像。历史更新统一见 [CHANGELOG](../CHANGELOG.md)。
