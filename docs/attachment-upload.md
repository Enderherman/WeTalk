# 附件上传与读取

附件采用“创建消息占位 → 上传字节 → 完成通知”的两阶段流程。所有接口要求登录，路径以 `/api/chat` 为前缀。消息类型 `messageType` 与附件类型 `fileType` 是两个不同字段。

## 创建与上传

1. `POST /sendMessage` 创建 `messageType=5` 的附件占位，返回服务器 `messageId`。
2. `POST /uploadFile` 使用 multipart 提交 `messageId`、非空 `file` 和可选 `cover`。
3. 服务端保存最终文件名和实际字节数，将状态从 0（待上传）改为 1（完成），事务提交后推送 type 6。

| 创建字段 | 规则 |
|---|---|
| `contactId` | 当前可发送消息的好友或群 |
| `messageContent` | 必填，最多 500 个 UTF-16 代码单元 |
| `messageType` | 附件固定为 `5` |
| `fileName` | 非空白，最多 200 个 UTF-16 代码单元，不能包含路径分隔符、控制字符或仅为 `.` / `..` |
| `fileSize` | 正数，上传后由实际字节数替换 |
| `fileType` | `0` 图片、`1` 音视频、`2` 普通文件 |
| `clientMessageId` | 附件不接受；该 UUID 幂等字段只用于文字消息 |

上传时必须是占位消息的原发送者，并重新查询当前双向好友关系或有效群成员关系。删除、拉黑或退群后不能继续上传旧占位；不能使用文字消息作为附件。服务器最终保存上传文件的原名，不使用客户端缓存文件名代替展示名称。

## 大小与格式

系统配额以 MiB（1024² 字节）计：图片使用 `maxImageSize`、音视频使用 `maxVideoSize`、普通文件使用 `maxFileSize`。识别到图片或视频扩展名时还取对应配额的较小值，不能仅靠改变声明类型放宽上限。音视频声明为 type 1 时，即使后缀改成普通文件，仍受视频配额约束。

应用默认配额为图片 200、音视频 500、普通文件 5000 MiB；HTTP 默认 `MAX_UPLOAD_SIZE=500MB` 同时约束单文件和整个请求，实际可上传上限还受反向代理限制。调整配额时需要同时核对各层限制。

上传后缀允许为空，否则必须是点号加 1–16 个 ASCII 字母或数字。图片声明 `fileType=0` 要求扩展名、MIME 与文件签名一致；支持范围见[图片格式说明](chat-image-compatibility.md)。服务端不会因为 MIME 缺失而跳过图片验证。

`cover` 可省略；提供时必须是有效的 PNG/JPEG/GIF/BMP/WebP 图片，并受系统图片配额约束。客户端可生成 PNG 视频首帧；服务端封面验证器本身不只接受 PNG。封面不接受 `.mjpeg`。

## 重试与一致性

同一消息的上传通过数据库行锁串行处理。文件先写入暂存路径；传输失败会清理暂存文件并保留待上传状态，可使用原 `messageId` 重试，无需另建占位。

已完成附件的重试必须同名且内容字节完全相同；若重传封面，其字节也必须与原封面相同。省略封面不会删除原封面。满足条件时直接返回成功，不重复写消息或广播；换名字、换内容或新增不同封面均拒绝。

type 6 携带最终元数据，数据库仍保留 type 5。客户端按 `messageId` 合并占位、HTTP 成功和 WebSocket 完成事件，避免重复气泡或覆盖最终字节数。详见[多端消息同步](multi-device-message-sync.md)。

## 下载与播放

| 接口 | 行为 |
|---|---|
| `POST /downloadFile` | 表单 `fileId=<消息ID>`、`showCover=false` 下载原附件；`true` 读取封面 |
| `GET /streamMedia?fileId=<消息ID>` | 仅提供已完成的 `fileType=1` 且后缀受支持的音视频；支持单区间 Range |

文件读取要求消息为已完成的 type 5。私聊只允许原发送者与原收件人；群附件要求当前有效群成员。私聊历史附件读取不要求仍为好友，上传与下载的关系规则不能混用。

媒体流返回完整 200、单区间 206 或不可满足的 416；响应带 `Accept-Ranges`、相应 `Content-Range`、`private, no-store` 和 `nosniff`。多区间请求不支持。普通文件下载与流式读取都不暴露服务器绝对路径。

## 维护与验证入口

- [ChatController](../src/main/java/top/enderherman/wetalk/controller/ChatController.java)：请求字段、下载和 Range。
- [ChatMessageServiceImpl](../src/main/java/top/enderherman/wetalk/service/impl/ChatMessageServiceImpl.java)：占位、权限、行锁、暂存与完成通知。
- [MessageFileUploadSafetyTest](../src/test/java/top/enderherman/wetalk/service/MessageFileUploadSafetyTest.java)、[ChatFileAccessTest](../src/test/java/top/enderherman/wetalk/service/ChatFileAccessTest.java)、[MediaRangeStreamTest](../src/test/java/top/enderherman/wetalk/controller/MediaRangeStreamTest.java)：类型、配额、重试、权限及分段读取回归。

历史验证记录集中在 [CHANGELOG](../CHANGELOG.md)。部署时还需用真实上传/下载字节核对存储和代理配置。
