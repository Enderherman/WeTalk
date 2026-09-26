package top.enderherman.wetalk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.query.ChatMessageQuery;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.ChatMessageMapper;
import top.enderherman.wetalk.service.impl.ChatMessageServiceImpl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMessageCancellationTest {

    @Mock
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    @Test
    void rejectsAnAiReplyOwnedByAnotherUser() {
        ChatMessage reply = new ChatMessage();
        reply.setMessageId(801);
        reply.setMessageType(MessageTypeEnum.AI_CHAT.getType());
        reply.setSendUserId(Constants.ROBOT_UID);
        reply.setContactId("U200");
        reply.setMessageContent("private partial answer");
        when(chatMessageMapper.selectByMessageId(801)).thenReturn(reply);

        TokenUserInfoDto caller = new TokenUserInfoDto();
        caller.setUserId("U100");

        assertThrows(BusinessException.class, () -> chatMessageService.cancelAiMessage(801, caller));
        verify(chatMessageMapper, never()).updateByMessageId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(801));
    }

    @Test
    void rejectsCancellingANonAiMessage() {
        ChatMessage reply = new ChatMessage();
        reply.setMessageId(802);
        reply.setMessageType(MessageTypeEnum.CHAT.getType());
        reply.setSendUserId(Constants.ROBOT_UID);
        reply.setContactId("U100");
        when(chatMessageMapper.selectByMessageId(802)).thenReturn(reply);

        TokenUserInfoDto caller = new TokenUserInfoDto();
        caller.setUserId("U100");

        assertThrows(BusinessException.class, () -> chatMessageService.cancelAiMessage(802, caller));
        verify(chatMessageMapper, never()).updateByMessageId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(802));
    }
}
