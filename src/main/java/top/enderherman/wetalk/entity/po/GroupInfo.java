package top.enderherman.wetalk.entity.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import top.enderherman.wetalk.entity.enums.DateTimePatternEnum;
import top.enderherman.wetalk.utils.DateUtils;

import java.io.Serializable;
import java.util.Date;


/**
 * 群组信息表
 */
@Data
public class GroupInfo implements Serializable {


	/**
	 * 群组id
	 */
	private String groupId;

	/**
	 * 群组昵称
	 */
	private String groupName;

	/**
	 * 群主用户id
	 */
	private String groupOwnId;

	/**
	 * 创建时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createTime;

	/**
	 * 创建时间
	 */
	private String groupNotice;

	/**
	 * 加入方式: 0:直接加入 1:管理员同意后加入
	 */
	private Integer joinType;

	/**
	 * 状态： 0:解散 1:正常
	 */
	private Integer status;

	/**
	 * 成员数
	 */
	private Integer memberCount;

	/**
	 *
	 */
	private String groupOwnerNickName;



	@Override
	public String toString (){
		return "群组id:"+(groupId == null ? "空" : groupId)+"，群组昵称:"+(groupName == null ? "空" : groupName)+"，群主用户id:"+(groupOwnId == null ? "空" : groupOwnId)+"，创建时间:"+(createTime == null ? "空" : DateUtils.format(createTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern()))+"，创建时间:"+(groupNotice == null ? "空" : groupNotice)+"，加入方式: 0:直接加入 1:管理员同意后加入:"+(joinType == null ? "空" : joinType)+"，状态： 0:解散 1:正常:"+(status == null ? "空" : status);
	}
}
