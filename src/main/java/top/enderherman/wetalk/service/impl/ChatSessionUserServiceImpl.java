package top.enderherman.wetalk.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.entity.dto.MessageSendDTO;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.enums.PageSize;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;
import top.enderherman.wetalk.entity.po.ChatSessionUser;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.ChatSessionUserQuery;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.mappers.ChatSessionUserMapper;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.service.ChatSessionUserService;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.webSocket.MessageHandler;

import jakarta.annotation.Resource;
import java.util.List;


/**
 * 会话用户表 业务接口实现
 */
@Service("chatSessionUserService")
public class ChatSessionUserServiceImpl implements ChatSessionUserService {

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private MessageHandler messageHandler;
    @Autowired
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;
    @Autowired
    private RedisComponent redisComponent;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<ChatSessionUser> findListByParam(ChatSessionUserQuery param) {
        return this.chatSessionUserMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(ChatSessionUserQuery param) {
        return this.chatSessionUserMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<ChatSessionUser> findListByPage(ChatSessionUserQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<ChatSessionUser> list = this.findListByParam(param);
        PaginationResultVO<ChatSessionUser> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(ChatSessionUser bean) {
        return this.chatSessionUserMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<ChatSessionUser> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.chatSessionUserMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<ChatSessionUser> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.chatSessionUserMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(ChatSessionUser bean, ChatSessionUserQuery param) {
        StringUtils.checkParam(param);
        return this.chatSessionUserMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(ChatSessionUserQuery param) {
        StringUtils.checkParam(param);
        return this.chatSessionUserMapper.deleteByParam(param);
    }

    /**
     * 根据UserIdAndContactId获取对象
     */
    @Override
    public ChatSessionUser getChatSessionUserByUserIdAndContactId(String userId, String contactId) {
        return this.chatSessionUserMapper.selectByUserIdAndContactId(userId, contactId);
    }

    /**
     * 根据UserIdAndContactId修改
     */
    @Override
    public Integer updateChatSessionUserByUserIdAndContactId(ChatSessionUser bean, String userId, String contactId) {
        return this.chatSessionUserMapper.updateByUserIdAndContactId(bean, userId, contactId);
    }

    /**
     * 根据UserIdAndContactId删除
     */
    @Override
    public Integer deleteChatSessionUserByUserIdAndContactId(String userId, String contactId) {
        return this.chatSessionUserMapper.deleteByUserIdAndContactId(userId, contactId);
    }


    /**
     * 更新聊天列表中名称
     */
    @Override
    public void updateRedundancyInfo(String contactName, String contactId) {
        ChatSessionUser updateChatSessionUser = new ChatSessionUser();
        updateChatSessionUser.setContactName(contactName);
        ChatSessionUserQuery query = new ChatSessionUserQuery();
        query.setContactId(contactId);
        chatSessionUserMapper.updateByParam(updateChatSessionUser, query);

        UserContactTypeEnum contactType = UserContactTypeEnum.getByPrefix(contactId);
        if (contactType == UserContactTypeEnum.GROUP) {
            //发送ws信息
            MessageSendDTO<String> messageSendDTO = new MessageSendDTO<>();
            messageSendDTO.setContactType(contactType.getType());
            messageSendDTO.setContactId(contactId);
            messageSendDTO.setExtentData(contactName);
            messageSendDTO.setMessageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType());
            messageHandler.sendMessage(messageSendDTO);
        } else if (contactType == UserContactTypeEnum.USER) {

            //更新有该用户好友的联系人的列表
            UserContactQuery cQuery = new UserContactQuery();
            cQuery.setContactType(contactType.getType());
            cQuery.setContactId(contactId);
            List<UserContact> userContactList = userContactMapper.selectList(cQuery);
            //List<String> userContactList = redisComponent.getUserContactList(contactId);

            for (UserContact userContact : userContactList) {
                //发送ws信息
                MessageSendDTO<String> messageSendDTO = new MessageSendDTO<>();
                messageSendDTO.setContactType(contactType.getType());
                messageSendDTO.setContactId(userContact.getUserId());
                messageSendDTO.setExtentData(contactName);
                messageSendDTO.setMessageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType());
                messageSendDTO.setSendUserId(contactId);
                messageSendDTO.setSendUserNickName(contactName);
                messageHandler.sendMessage(messageSendDTO);
            }
        }

    }
}