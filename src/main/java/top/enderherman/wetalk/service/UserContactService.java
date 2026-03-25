package top.enderherman.wetalk.service;

import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.entity.vo.UserContactSearchResultVO;

import java.util.List;


/**
 * 联系人表 业务接口
 */
public interface UserContactService {

	/**
	 * 根据条件查询列表
	 */
	List<UserContact> findListByParam(UserContactQuery param);

	/**
	 * 根据条件查询列表
	 */
	Integer findCountByParam(UserContactQuery param);

	/**
	 * 分页查询
	 */
	PaginationResultVO<UserContact> findListByPage(UserContactQuery param);

	/**
	 * 新增
	 */
	Integer add(UserContact bean);

	/**
	 * 批量新增
	 */
	Integer addBatch(List<UserContact> listBean);

	/**
	 * 批量新增/修改
	 */
	Integer addOrUpdateBatch(List<UserContact> listBean);

	/**
	 * 多条件更新
	 */
	Integer updateByParam(UserContact bean,UserContactQuery param);

	/**
	 * 多条件删除
	 */
	Integer deleteByParam(UserContactQuery param);

	/**
	 * 根据UserIdAndContactId查询对象
	 */
	UserContact getUserContactByUserIdAndContactId(String userId,String contactId);


	/**
	 * 根据UserIdAndContactId修改
	 */
	Integer updateUserContactByUserIdAndContactId(UserContact bean,String userId,String contactId);


	/**
	 * 根据UserIdAndContactId删除
	 */
	Integer deleteUserContactByUserIdAndContactId(String userId,String contactId);

	/**
	 * 搜索好友或群组
	 */
	UserContactSearchResultVO searchContact(String userId, String contactId);

	/**
	 * 申请加好友/群聊
	 */
	Integer apply(TokenUserInfoDto tokenUserInfoDto, String contactId, String applyInfo);

	/**
	 * 添加联系人
	 */
	void addContact(String applyUserId, String receiveUserId, String contactId, Integer contactType, String applyInfo);

	/**
	 * 查询联系人信息
	 */
	List<UserContact> loadContact(String userId, String contactType);

	/**
	 * 更改联系人类型
	 */
    void changeContactType(String userId, String contactId, Integer status);

	/**
	 * 添加机器人好有
	 */
    void addRobotContact(String userId);
}