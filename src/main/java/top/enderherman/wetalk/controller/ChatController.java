package top.enderherman.wetalk.controller;

import cn.hutool.core.util.ArrayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.ChatMessageService;
import top.enderherman.wetalk.service.RateLimitService;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController extends ABaseController {

    private static final Map<String, String> MEDIA_CONTENT_TYPES = Map.ofEntries(
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".avi", "video/x-msvideo"),
            Map.entry(".rmvb", "video/vnd.rn-realvideo"),
            Map.entry(".mkv", "video/x-matroska"),
            Map.entry(".mov", "video/quicktime"),
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".wma", "audio/x-ms-wma"),
            Map.entry(".flac", "audio/flac"),
            Map.entry(".aac", "audio/aac"),
            Map.entry(".wav", "audio/wav"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".m4a", "audio/mp4"),
            Map.entry(".m4b", "audio/mp4"));

    private record MediaRange(long start, long end, boolean partial) {}

    @Resource
    private AppConfig appConfig;


    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private RateLimitService rateLimitService;

    /**
     * 发送消息
     */
    @GlobalInterceptor
    @PostMapping("/sendMessage")
    public BaseResponse<MessageSendDTO<?>> sendMessage(HttpServletRequest request,
                                                       @NotNull String contactId,
                                                       @NotNull @Size(max = 500) String messageContent,
                                                       @NotNull Integer messageType,
                                                       Long fileSize,
                                                       String fileName,
                                                       Integer fileType,
                                                       @Size(max = 36) @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String clientMessageId) {
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(messageType);
        if (messageTypeEnum == null || !ArrayUtil.contains(new Integer[]{MessageTypeEnum.CHAT.getType(), MessageTypeEnum.MEDIA_CHAT.getType()}, messageType)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (clientMessageId != null && !MessageTypeEnum.CHAT.equals(messageTypeEnum)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        rateLimitService.enforce("chat-send", tokenUserInfoDto.getUserId(), 120, 60);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContactId(contactId);
        chatMessage.setMessageContent(messageContent);
        chatMessage.setMessageType(messageType);
        chatMessage.setFileName(fileName);
        chatMessage.setFileType(fileType);
        chatMessage.setFileSize(fileSize);
        chatMessage.setClientMessageId(clientMessageId);
        MessageSendDTO<?> messageSendDTO = chatMessageService.saveMessage(chatMessage, tokenUserInfoDto);
        return BaseResponse.success(messageSendDTO);
    }

    /** 停止当前用户发起的 AI 回复。 */
    @GlobalInterceptor
    @PostMapping("/cancelAiMessage")
    public BaseResponse<MessageSendDTO<?>> cancelAiMessage(HttpServletRequest request,
                                                           @NotNull @Min(1) Integer messageId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        return BaseResponse.success(chatMessageService.cancelAiMessage(messageId, tokenUserInfoDto));
    }

    /**
     * 按游标读取一对一或群聊历史消息。
     */
    @GlobalInterceptor
    @PostMapping("/loadHistory")
    public BaseResponse<PaginationResultVO<ChatMessage>> loadHistory(HttpServletRequest request,
                                                                     @NotNull String contactId,
                                                                     @Min(1) Integer beforeMessageId,
                                                                     @Min(1) @Max(50) Integer pageSize) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        return BaseResponse.success(chatMessageService.loadHistory(tokenUserInfoDto, contactId, beforeMessageId, pageSize));
    }

    /**
     * 文件上传
     */
    @GlobalInterceptor
    @PostMapping("/uploadFile")
    public BaseResponse<String> uploadFile(HttpServletRequest request,
                                           @NotNull Integer messageId,
                                           @NotNull MultipartFile file,
                                           MultipartFile cover) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        chatMessageService.saveMessageFile(tokenUserInfoDto.getUserId(), messageId, file, cover);
        return BaseResponse.success("上传成功");
    }

    /**
     * 文件下载
     */
    @GlobalInterceptor
    @PostMapping("/downloadFile")
    public void downloadFile(HttpServletRequest request,
                             HttpServletResponse response,
                             @NotNull String fileId,
                             @NotNull Boolean showCover) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        File file;
        if (!StringUtils.isNumber(fileId)) {
            if (!fileId.matches("[a-zA-Z0-9_-]{1,128}")) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            String avatarPath = appConfig.getProjectFolder() + Constants.FILE_FOLDER
                    + Constants.AVATAR_FOLDER + fileId + Constants.IMAGE_SUFFIX;
            if (Boolean.TRUE.equals(showCover)) {
                avatarPath += Constants.COVER_IMAGE_SUFFIX;
            }
            file = new File(avatarPath);
            if (!file.isFile()) {
                throw new BusinessException(ResponseCodeEnum.CODE_602);
            }
        } else {
            try {
                file = chatMessageService.downloadFile(tokenUserInfoDto, Long.parseLong(fileId), showCover);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment");
        response.setContentLengthLong(file.length());
        try (FileInputStream in = new FileInputStream(file)) {
            in.transferTo(response.getOutputStream());
        } catch (IOException e) {
            log.error("File download I/O failure", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    /** Stream an authorized audio/video message with single-range HTTP support for browser media controls. */
    @GlobalInterceptor
    @GetMapping("/streamMedia")
    public void streamMedia(HttpServletRequest request,
                            HttpServletResponse response,
                            @NotNull @Min(1) Long fileId) {
        TokenUserInfoDto user = getTokenUserDto(request);
        File file = chatMessageService.downloadFile(user, fileId, false);
        ChatMessage message = chatMessageService.getChatMessageByMessageId(fileId.intValue());
        if (message == null || !Integer.valueOf(1).equals(message.getFileType())
                || !Integer.valueOf(1).equals(message.getStatus()) || StringUtils.isEmpty(message.getFileName())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String suffix = StringUtils.getFileSuffix(message.getFileName()).toLowerCase(Locale.ROOT);
        String contentType = MEDIA_CONTENT_TYPES.get(suffix);
        if (contentType == null) throw new BusinessException(ResponseCodeEnum.CODE_600);

        long fileLength = file.length();
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(contentType);
        MediaRange range = parseRange(request.getHeader("Range"), fileLength);
        if (range == null) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + fileLength);
            response.setContentLengthLong(0);
            return;
        }

        long contentLength = fileLength == 0 ? 0 : range.end() - range.start() + 1;
        response.setStatus(range.partial() ? 206 : 200);
        if (range.partial()) {
            response.setHeader("Content-Range", "bytes " + range.start() + "-" + range.end() + "/" + fileLength);
        }
        response.setContentLengthLong(contentLength);
        if (contentLength == 0) return;

        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(range.start());
            OutputStream output = response.getOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long remaining = contentLength;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                output.write(buffer, 0, read);
                remaining -= read;
            }
            output.flush();
        } catch (IOException error) {
            log.warn("Media stream interrupted for message {}", fileId);
        }
    }

    private MediaRange parseRange(String header, long fileLength) {
        if (StringUtils.isEmpty(header)) {
            return new MediaRange(0, fileLength == 0 ? -1 : fileLength - 1, false);
        }
        if (fileLength <= 0) return null;
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6) || value.indexOf(',') >= 0) return null;
        String spec = value.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0 || spec.indexOf('-', dash + 1) >= 0) return null;

        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        if (startText.isEmpty() && endText.isEmpty()) return null;
        try {
            long start;
            long end;
            if (startText.isEmpty()) {
                long suffixLength = Long.parseLong(endText);
                if (suffixLength <= 0) return null;
                start = Math.max(0, fileLength - suffixLength);
                end = fileLength - 1;
            } else {
                start = Long.parseLong(startText);
                end = endText.isEmpty() ? fileLength - 1 : Long.parseLong(endText);
            }
            if (start < 0 || start >= fileLength || end < start) return null;
            end = Math.min(end, fileLength - 1);
            return new MediaRange(start, end, true);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
