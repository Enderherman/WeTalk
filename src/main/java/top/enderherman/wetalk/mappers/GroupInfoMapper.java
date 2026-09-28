package top.enderherman.wetalk.mappers;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 群组信息表 数据库操作接口
 */
public interface GroupInfoMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据GroupId更新
	 */
	 Integer updateByGroupId(@Param("bean") T t,@Param("groupId") String groupId);


	/**
	 * 根据GroupId删除
	 */
	 Integer deleteByGroupId(@Param("groupId") String groupId);


	/**
	 * 根据GroupId获取对象
	 */
	 T selectByGroupId(@Param("groupId") String groupId);

	/**
	 * 模糊查找有效群昵称，仅返回群编号和群昵称。
	 */
	 List<T> selectActiveGroupsByNameFuzzy(@Param("keyword") String keyword,
	                                            @Param("offset") Integer offset,
	                                            @Param("pageSize") Integer pageSize);


}
