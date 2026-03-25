package top.enderherman.wetalk.mappers;

import org.apache.ibatis.annotations.Param;

/**
 * 聊天消息表 数据库操作接口
 */
public interface ChatMessageMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据MessageId更新
	 */
	 Integer updateByMessageId(@Param("bean") T t,@Param("messageId") Integer messageId);


	/**
	 * 根据MessageId删除
	 */
	 Integer deleteByMessageId(@Param("messageId") Integer messageId);


	/**
	 * 根据MessageId获取对象
	 */
	 T selectByMessageId(@Param("messageId") Integer messageId);


}
