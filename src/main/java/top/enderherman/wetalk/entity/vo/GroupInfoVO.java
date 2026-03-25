package top.enderherman.wetalk.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.enderherman.wetalk.entity.po.GroupInfo;
import top.enderherman.wetalk.entity.po.UserContact;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInfoVO {

    private GroupInfo groupInfo;
    private List<UserContact> userContactList;
}
