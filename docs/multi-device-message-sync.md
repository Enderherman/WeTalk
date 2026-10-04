# 同账号多端消息同步

同一账号可同时保留一个浏览器会话和一个桌面会话；同类重新登录会替换旧会话。本协议规定消息的双方视角及客户端合并方式，不要求 HTTP 响应与 WebSocket 事件按固定顺序到达。

## 私聊分发

文字 type **2**、附件占位 type **5** 和上传完成 type **6** 各向发送者与接收者的在线连接分发。发起 HTTP 的设备也会收到本人的回显，因此不能把每个事件都当作新气泡。

| 字段 | 接收方设备 | 发送方设备 |
|---|---|---|
| `messageId` / `sessionId` | 服务器消息与会话 ID | 相同 |
| `sendUserId` / `sendUserNickName` | 实际发送者 | 保持实际发送者，用于识别本人消息 |
| `contactId` | 发送者编号 | 实际收件人编号 |
| `contactName` | 发送者真实昵称 | 本人会话中的收件人真实名称；未知时不填 |
| `clientMessageId` | 文字原始 UUID，存在时携带 | 与 HTTP 返回一致，用于确认待发记录 |

双方使用独立 DTO 副本，分发不修改共享原始载荷。发送方名称只取会话的真实 `contactName`，不会把私有 `remark` 带进对端消息。无关账号不接收事件。

发送者与收件人编号相同时不重复分发。群聊使用群通道，本身覆盖发送者设备，不叠加私聊镜像。

## 文件完成事件

type 6 在文件持久化事务提交后发送，复制最终消息元数据并将事件类型改为 6：

- 消息与会话：`messageId`、`sessionId`、`contactType`、按上表转换的 `contactId`。
- 发送信息：`sendUserId`、`sendUserNickName`、原始 `sendTime`、`messageContent`。
- 文件信息：`status=1`、最终 `fileName`、真实 `fileSize`、`fileType`。

数据库历史仍为 type 5；事件不返回服务器绝对文件路径。客户端将最终字段合并到原占位，完成事件先到时也可恢复该条消息。缺失字段不能覆盖已知值，但事件中实际存在的最终文件名、字节数和状态必须替换旧占位值。

## AI 提问与回复

向 `Urobot` 发送的提问插入后立即发布 type 2，两台本人设备得到相同服务器 ID 和幂等键，目标保持 `Urobot`，名称取当前机器人配置。另一端无需等到 AI 完成才展示提问。

回复使用原有事件：14 初始化、15 累计全文、16 终态。终态 1/2/3 分别为完成、用户停止和提供方失败。累计片段替换已有生成文本，不能按增量逐段追加。

## 控制事件与未读

type 17 为私聊对端已读游标，type 18 为本人私有备注同步。它们保持各自的定向规则，不做发送者额外镜像，不插入聊天历史或计为新消息。其他控制事件也不能仅因为包含 `contactId` 就套用私聊回显逻辑。

客户端应遵循以下合并规则：

1. 按服务器 `messageId` 去重，使用文字 `clientMessageId` 将本地待发记录与服务器消息关联。
2. HTTP 成功先到或 WebSocket 回显先到都只确认一次；本人消息不增加未读，不生成与自己的错误会话。
3. type 6 更新原消息的完成状态和最终元数据，不再创建第二条附件。
4. AI 提问属于本人消息；新机器人回复才按接收消息规则更新未读。
5. 断线后从历史和 INIT 恢复持久化状态，不能只依赖瞬时事件。

文字 `clientMessageId` 的唯一性范围为发送者；相同键重试必须保持目标与内容一致。附件通过原消息 ID 重试上传，详见[附件契约](attachment-upload.md)。

## 维护与验证入口

[ChannelContextUtils](../src/main/java/top/enderherman/wetalk/webSocket/ChannelContextUtils.java)处理连接和视角，[ChatMessageServiceImpl](../src/main/java/top/enderherman/wetalk/service/impl/ChatMessageServiceImpl.java)构造 AI 提问与文件完成载荷。

[SameAccountFanoutTest](../src/test/java/top/enderherman/wetalk/webSocket/SameAccountFanoutTest.java)覆盖双方多通道、原 DTO、私有名称与控制事件；[MultiDeviceMessagePayloadTest](../src/test/java/top/enderherman/wetalk/service/MultiDeviceMessagePayloadTest.java)覆盖 AI 提问及提交后的最终文件字段。真实双端验收还需检查气泡去重、后台未读和附件元数据；历史结果与版本边界集中在 [CHANGELOG](../CHANGELOG.md)。
