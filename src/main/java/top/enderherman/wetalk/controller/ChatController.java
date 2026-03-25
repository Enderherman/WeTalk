package top.enderherman.wetalk.controller;

import cn.hutool.core.util.ArrayUtil;
import lombok.extern.slf4j.Slf4j;
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
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.ChatMessageService;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@Slf4j
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
                                                       @NotNull @Max(500) String messageContent,
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

    /**
     * 文件上传
     */
    @GlobalInterceptor
    @PostMapping("/uploadFile")
    public BaseResponse<String> uploadFile(HttpServletRequest request,
                                           @NotNull Integer messageId,
                                           @NotNull MultipartFile file,
                                           @NotNull MultipartFile cover) {
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
        OutputStream out = null;
        FileInputStream in = null;
        try {
            File file;
            //获取头像
            if (!StringUtils.isNumber(fileId)) {
                String avatarFolderName = Constants.FILE_FOLDER + Constants.AVATAR_FOLDER;
                String avatarPath = appConfig.getProjectFolder() + avatarFolderName + fileId + Constants.IMAGE_SUFFIX;
                if (showCover) {
                    avatarPath = avatarPath + Constants.COVER_IMAGE_SUFFIX;
                }
                file = new File(avatarPath);
                if (!file.exists()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
            } else {
                file = chatMessageService.downloadFile(tokenUserInfoDto, Long.parseLong(fileId), showCover);
            }
            response.setContentType("application/x-msdownload; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;");
            response.setContentLengthLong(file.length());
            in = new FileInputStream(file);
            byte[] byteData = new byte[1024];
            out = response.getOutputStream();
            int len;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            response.setHeader("content-type", "application/json");
            log.error("下载文件失败");
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error("IO异常", e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("IO异常", e);
                }
            }
        }
    }
}
