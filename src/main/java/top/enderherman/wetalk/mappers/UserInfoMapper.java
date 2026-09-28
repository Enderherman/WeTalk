package top.enderherman.wetalk.mappers;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 数据库操作接口
 */
public interface UserInfoMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据UserId更新
	 */
	 Integer updateByUserId(@Param("bean") T t,@Param("userId") String userId);


	/**
	 * 根据UserId删除
	 */
	 Integer deleteByUserId(@Param("userId") String userId);


	/**
	 * 根据UserId获取对象
	 */
	 T selectByUserId(@Param("userId") String userId);


	/**
	 * 根据Email更新
	 */
	 Integer updateByEmail(@Param("bean") T t,@Param("email") String email);


	/**
	 * 根据Email删除
	 */
	 Integer deleteByEmail(@Param("email") String email);


	/**
	 * 根据Email获取对象
	 */
	 T selectByEmail(@Param("email") String email);

	/**
	 * 读取可用于联系人搜索的公开用户资料，不查询邮箱、密码或签名。
	 */
	 T selectPublicContactByUserId(@Param("userId") String userId);

	/**
	 * 按邮箱精确查找可用于联系人搜索的公开资料，不查询邮箱或密码。
	 */
	 T selectPublicContactByEmail(@Param("email") String email);

	/**
	 * 模糊查找启用中的用户昵称，仅返回公开资料字段。
	 */
	 List<T> selectActiveContactsByNicknameFuzzy(@Param("keyword") String keyword,
	                                                   @Param("offset") Integer offset,
	                                                   @Param("pageSize") Integer pageSize);


}
