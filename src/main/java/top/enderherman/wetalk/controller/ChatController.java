package top.enderherman.wetalk.controller;

import cn.hutool.core.util.ArrayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
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
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@Slf4j
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController extends ABaseController {

    @Resource
    private AppConfig appConfig;


    @Resource
    private ChatMessageService chatMessageService;

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
                                                       Integer fileType) {
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(messageType);
        if (messageTypeEnum == null || !ArrayUtil.contains(new Integer[]{MessageTypeEnum.CHAT.getType(), MessageTypeEnum.MEDIA_CHAT.getType()}, messageType)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContactId(contactId);
        chatMessage.setMessageContent(messageContent);
        chatMessage.setMessageType(messageType);
        chatMessage.setFileName(fileName);
        chatMessage.setFileType(fileType);
        chatMessage.setFileSize(fileSize);
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
}
