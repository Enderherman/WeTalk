package top.enderherman.wetalk.service;

import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.enums.MessageTypeEnum;
import top.enderherman.wetalk.entity.po.GroupInfo;
import top.enderherman.wetalk.entity.query.GroupInfoQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;

import java.util.List;


/**
 * 群组信息表 业务接口
 */
public interface GroupInfoService {

    /**
     * 根据条件查询列表
     */
    List<GroupInfo> findListByParam(GroupInfoQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(GroupInfoQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<GroupInfo> findListByPage(GroupInfoQuery param);

    /**
     * 新增
     */
    Integer add(GroupInfo bean);

    /**
     * 批量新增
     */
    Integer addBatch(List<GroupInfo> listBean);

    /**
     * 批量新增/修改
     */
    Integer addOrUpdateBatch(List<GroupInfo> listBean);

    /**
     * 多条件更新
     */
    Integer updateByParam(GroupInfo bean, GroupInfoQuery param);

    /**
     * 多条件删除
     */
    Integer deleteByParam(GroupInfoQuery param);

    /**
     * 根据GroupId查询对象
     */
    GroupInfo getGroupInfoByGroupId(String groupId);


    /**
     * 根据GroupId修改
     */
    Integer updateGroupInfoByGroupId(GroupInfo bean, String groupId);


    /**
     * 根据GroupId删除
     */
    Integer deleteGroupInfoByGroupId(String groupId);

    /**
     *创建群组
     */
    void saveGroup(GroupInfo groupInfo, MultipartFile avatarFile, MultipartFile avatarCover);

    /**
     * 退群
     */
    void leaveGroup(String userId, String groupId, MessageTypeEnum messageTypeEnum);

    /**
     * 解散群组
     */
    void dissolutionGroup(String groupOwnerId, String groupId);

    /**
     * 移除或者添加群成员
     */
    void addOrRemoveGroupUser(TokenUserInfoDto tokenUserInfoDto, String groupId, String selectContacts,Integer opType);
}