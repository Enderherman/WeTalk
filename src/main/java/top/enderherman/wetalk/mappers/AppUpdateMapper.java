package top.enderherman.wetalk.mappers;

import org.apache.ibatis.annotations.Param;

/**
 * app发布表 数据库操作接口
 */
public interface AppUpdateMapper<T,P> extends BaseMapper<T,P> {

    Integer lockReleaseCatalog();
    java.util.List<T> selectAllForUpdate();
    T selectByIdForUpdate(@Param("id") Integer id);
    Integer updateDraftById(@Param("bean") T bean, @Param("id") Integer id);
    Integer deleteDraftById(@Param("id") Integer id);
    Integer updatePublicationState(@Param("id") Integer id, @Param("expectedStatus") Integer expectedStatus,
                                   @Param("status") Integer status, @Param("grayscaleUid") String grayscaleUid);

	/**
	 * 根据Id更新
	 */
	 Integer updateById(@Param("bean") T t,@Param("id") Integer id);


	/**
	 * 根据Id删除
	 */
	 Integer deleteById(@Param("id") Integer id);


	/**
	 * 根据Id获取对象
	 */
	 T selectById(@Param("id") Integer id);



     java.util.List<T> selectVisibleUpdates(@Param("uid") String uid);
}
