package top.enderherman.wetalk.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.GroupStatusEnum;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.enums.UserContactStatusEnum;
import top.enderherman.wetalk.entity.po.GroupInfo;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.GroupInfoQuery;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.GroupInfoVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.GroupInfoService;
import top.enderherman.wetalk.service.UserContactService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 群组信息表 Controller
 */
@RequestMapping("/group")
@Validated
@RestController("groupInfoController")

public class GroupInfoController extends ABaseController {

    @Resource
    private GroupInfoService groupInfoService;

    @Resource
    private UserContactService userContactService;

    /**
     * 新增群组 或 修改群组信息
     */
    @PostMapping("/saveGroup")
    @GlobalInterceptor
    public BaseResponse<?> saveGroup(HttpServletRequest request,
                                     String groupId,
                                     @NotNull String groupName,
                                     String groupNotice,
                                     @NotNull Integer joinType,
                                     MultipartFile avatarFile,
                                     MultipartFile coverFile) {
        TokenUserInfoDto tokenUserDto = getTokenUserDto(request);
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setGroupId(groupId);
        groupInfo.setGroupName(groupName);
        groupInfo.setGroupNotice(groupNotice);
        groupInfo.setGroupOwnId(tokenUserDto.getUserId());
        groupInfo.setJoinType(joinType);
        groupInfoService.saveGroup(groupInfo, avatarFile, coverFile);
        return getSuccessResponseVO(null);
    }

    /**
     * 查看我的群组
     */
    @PostMapping("/loadMyGroup")
    @GlobalInterceptor
    public BaseResponse<?> loadMyGroup(HttpServletRequest request) {
        TokenUserInfoDto userDto = getTokenUserDto(request);
        GroupInfoQuery query = new GroupInfoQuery();
        query.setStatus(GroupStatusEnum.NORMAL.getStatus());
        query.setQueryMemberCount(true);
        query.setGroupOwnId(userDto.getUserId());
        query.setOrderBy("create_time desc");
        List<GroupInfo> groupInfoList = groupInfoService.findListByParam(query);
        return getSuccessResponseVO(groupInfoList);
    }

    /**
     * 获取群组信息详情
     */
    @RequestMapping("/getGroupInfo")
    @GlobalInterceptor
    public BaseResponse<?> getGroupInfo(HttpServletRequest request,
                                        @NotNull String groupId) {
        TokenUserInfoDto userDto = getTokenUserDto(request);
        GroupInfo groupInfo = getGroupDetailInfo(userDto.getUserId(), groupId);

        UserContactQuery query = new UserContactQuery();
        query.setContactId(groupId);
        Integer memberCount = userContactService.findCountByParam(query);
        groupInfo.setMemberCount(memberCount);
        return getSuccessResponseVO(groupInfo);

    }

    /**
     * 聊天中获取群组信息详情
     */
    @RequestMapping("/getGroupInfo4Chat")
    @GlobalInterceptor
    public BaseResponse<?> getGroupInfo4Chat(HttpServletRequest request,
                                             @NotNull String groupId) {
        TokenUserInfoDto userDto = getTokenUserDto(request);
        GroupInfo groupInfo = getGroupDetailInfo(userDto.getUserId(), groupId);
        UserContactQuery query = new UserContactQuery();
        query.setContactId(groupId);
        query.setQueryUserInfo(true);

        query.setOrderBy("create_time asc");
        query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        List<UserContact> userContactList = userContactService.findListByParam(query);
        GroupInfoVO groupInfoVO = new GroupInfoVO(groupInfo, userContactList);
        return getSuccessResponseVO(groupInfoVO);

    }

    @GlobalInterceptor
    @PostMapping("/leaveGroup")
    public BaseResponse<String> leaveGroup(HttpServletRequest request,
                                           @NotNull String groupId) {
        TokenUserInfoDto userDto = getTokenUserDto(request);
        groupInfoService.leaveGroup(userDto.getUserId(), groupId, MessageTypeEnum.LEAVE_GROUP);
        return BaseResponse.success("退群成功");
    }

    /**
     * 添加或移除群聊
     */
    @GlobalInterceptor
    @PostMapping("/addOrRemoveGroupUser")
    public BaseResponse<String> addOrRemoveGroupUser(HttpServletRequest request,
                                                     @NotNull String groupId,
                                                     @NotNull String selectContacts,
                                                     @NotNull Integer opType) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        groupInfoService.addOrRemoveGroupUser(tokenUserInfoDto, groupId, selectContacts, opType);
        return BaseResponse.success(opType == 0 ? "移除成功" : "添加成功");
    }

    /**
     * 直接解散群组
     */
    @GlobalInterceptor
    @RequestMapping("/dissolutionGroup")
    public BaseResponse<?> dissolutionGroup(HttpServletRequest request, @NotNull String groupId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        groupInfoService.dissolutionGroup(tokenUserInfoDto.getUserId(), groupId);
        return BaseResponse.success();
    }


    /**
     * 获取群聊信息
     */
    private GroupInfo getGroupDetailInfo(String userId, @NotNull String groupId) {
        //1.先校验是不是这个群的
        UserContact userContact = userContactService.getUserContactByUserIdAndContactId(userId, groupId);
        if (userContact == null || !UserContactStatusEnum.FRIEND.getStatus().equals(userContact.getStatus())) {
            throw new BusinessException("您已退出群聊或群聊不存在");
        }

        GroupInfo groupInfo = groupInfoService.getGroupInfoByGroupId(groupId);
        if (groupInfo == null || !GroupStatusEnum.NORMAL.getStatus().equals(groupInfo.getStatus())) {
            throw new BusinessException("群聊不存在或已解散");
        }
        return groupInfo;
    }


}