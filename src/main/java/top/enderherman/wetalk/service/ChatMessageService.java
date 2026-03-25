package top.enderherman.wetalk.service;

import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;

import java.io.File;
import java.util.List;



/**
 * 聊天消息表 业务接口
 */
public interface ChatMessageService {

	/**
	 * 根据条件查询列表
	 */
	List<ChatMessage> findListByParam(ChatMessageQuery param);

	/**
	 * 根据条件查询列表
	 */
	Integer findCountByParam(ChatMessageQuery param);

	/**
	 * 分页查询
	 */
	PaginationResultVO<ChatMessage> findListByPage(ChatMessageQuery param);

	/**
	 * 新增
	 */
	Integer add(ChatMessage bean);

	/**
	 * 批量新增
	 */
	Integer addBatch(List<ChatMessage> listBean);

	/**
	 * 批量新增/修改
	 */
	Integer addOrUpdateBatch(List<ChatMessage> listBean);

	/**
	 * 多条件更新
	 */
	Integer updateByParam(ChatMessage bean,ChatMessageQuery param);

	/**
	 * 多条件删除
	 */
	Integer deleteByParam(ChatMessageQuery param);

	/**
	 * 根据MessageId查询对象
	 */
	ChatMessage getChatMessageByMessageId(Integer messageId);


	/**
	 * 根据MessageId修改
	 */
	Integer updateChatMessageByMessageId(ChatMessage bean,Integer messageId);


	/**
	 * 根据MessageId删除
	 */
	Integer deleteChatMessageByMessageId(Integer messageId);

	/**
	 * 保存消息
	 */
    MessageSendDTO<?> saveMessage(ChatMessage chatMessage, TokenUserInfoDto tokenUserInfoDto);

	/**
	 * 上传文件
	 */
	void saveMessageFile(String userId, Integer messageId, MultipartFile file, MultipartFile cover);


	/**
	 * 文件下载
	 */
	File downloadFile(TokenUserInfoDto tokenUserInfoDto, Long fileId, Boolean showCover);
}