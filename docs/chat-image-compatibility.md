# 聊天图片格式与 MIME

后端图片验证同时检查文件非空、大小、扩展名、MIME 和起始签名。扩展名和 MIME 比较不区分大小写；缺少 MIME 不会自动放行。

## 支持范围

| 后缀 | MIME | 聊天图片 | 资料头像、群头像、机器人头像与封面 |
|---|---|---|---|
| `.png` | `image/png` | 支持 | 支持 |
| `.jpg` / `.jpeg` | `image/jpeg` | 支持 | 支持 |
| `.gif` | `image/gif` | 支持 | 支持 |
| `.bmp` | `image/bmp` | 支持 | 支持 |
| `.webp` | `image/webp` | 支持 | 支持 |
| `.mjpeg` | 见下文 | 支持 | 不支持 |

这里“聊天图片”指 `messageType=5`、`fileType=0`。聊天图片与聊天附件封面使用系统图片大小配额；资料类头像/封面默认单文件最多 10 MiB。

签名检查用于识别常见格式头部，不等同完整图片解码，也不提供格式转换。客户端仍需处理浏览器或系统解码失败。

## .mjpeg 兼容规则

聊天图片将 `.mjpeg` 当作 JPEG 别名，规范 MIME 为 `image/jpeg`。为兼容既有桌面上传，还接受以下 MIME：

- `video/x-motion-jpeg`
- `image/x-mjpeg`
- `video/mjpeg`

上述分支都要求 JPEG 起始签名 `FF D8 FF`，并执行大小检查。这里不表示服务端支持把任意 MJPEG 视频流转换成静态图片。

`.mjpeg` 也在图片扩展名配额列表中：即便以普通文件声明，仍不能超过图片配额。但常规文件的格式策略与图片声明不同，不应将普通附件上传成功视作图片解码验证。

## 客户端要求

当 File API 返回空 MIME 时，客户端可按支持的后缀补齐规范 MIME，并保留原文件名和内容；非空且明显不匹配的 MIME 不应盲目覆盖。资料头像和封面不要使用仅聊天图片接受的别名。

上传流程、配额叠加与重试见[附件说明](attachment-upload.md)；图片格式变化应同时检查 Web、Electron 和后端行为。

## 维护与验证入口

[ImageUploadValidator](../src/main/java/top/enderherman/wetalk/utils/ImageUploadValidator.java)统一实现格式与签名检查，聊天使用 `validateChatImage`，资料及封面使用 `validate`。

相关回归：[ImageUploadValidatorTest](../src/test/java/top/enderherman/wetalk/utils/ImageUploadValidatorTest.java)、[MessageFileUploadSafetyTest](../src/test/java/top/enderherman/wetalk/service/MessageFileUploadSafetyTest.java)、[UserProfileImageValidationTest](../src/test/java/top/enderherman/wetalk/service/UserProfileImageValidationTest.java)。覆盖 MIME 别名、错误签名、大小边界及头像拒绝 `.mjpeg`；历史变更见 [CHANGELOG](../CHANGELOG.md)。
