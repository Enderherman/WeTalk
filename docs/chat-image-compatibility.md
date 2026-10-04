# 聊天图片别名与 MIME 兼容

2026-10-04。保留客户端既有 `.mjpeg` 聊天图片能力，把它作为 JPEG 图片别名处理。

- 仅 `messageType=5/fileType=0` 的聊天附件允许 `.mjpeg`；资料头像、群头像与上传封面仍使用原有 PNG/JPEG/GIF/BMP/WebP 列表。
- `.mjpeg` 规范 MIME 是 `image/jpeg`；兼容旧桌面客户端的 `video/x-motion-jpeg`、`image/x-mjpeg`、`video/mjpeg`。所有分支仍检查 JPEG 起始签名，错误 MIME、非 JPEG 内容、空文件和超限文件均拒绝。
- `.mjpeg` 进入图片扩展名配额列表，即便声明普通文件类型，也不能绕过图片大小上限。
- 浏览器/File API 没有提供 MIME 时，由客户端按已支持的扩展名补规范 MIME 并保留原名和字节；服务端不会因为 MIME 为空跳过验证。非空且与图片扩展名不符的类型不会被客户端盲目改写。

验证：ImageUploadValidator/MessageFileUploadSafety/UserProfileImageValidation 共20项通过；完整 Maven `clean verify` 186项通过、无失败/跳过。新增测试覆盖别名 MIME、签名、大小配额、头像范围拒绝及真实临时文件读回。实际运行实例需升级对应后端后再做跨端上传复验。
