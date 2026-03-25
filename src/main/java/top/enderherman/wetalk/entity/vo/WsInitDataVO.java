package top.enderherman.wetalk.entity.vo;

import lombok.Data;
import top.enderherman.wetalk.entity.po.ChatMessage;
import top.enderherman.wetalk.entity.po.ChatSessionUser;

import java.io.Serializable;
import java.util.List;

@Data
public class WsInitDataVO implements Serializable {

        private List<ChatSessionUser> chatSessionList;

        private List<ChatMessage> chatMessageList;

        private Integer applyCount;



}
