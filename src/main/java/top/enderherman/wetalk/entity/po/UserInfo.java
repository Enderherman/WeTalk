package top.enderherman.wetalk.entity.po;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.enums.DateTimePatternEnum;
import top.enderherman.wetalk.utils.DateUtils;

import java.io.Serializable;
import java.util.Date;


/**
 * 用户表
 */
@Data
public class UserInfo implements Serializable {


    /**
     * 用户id
     */
    private String userId;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 添加好友方式: 0:直接添加,1:同意后添加
     */
    private Integer joinType;

    /**
     * 性别:0男1女
     */
    private Integer sex;

    /**
     * 密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 个性签名
     */
    private String personalSignature;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    /**
     * 地区
     */
    private String areaName;

    /**
     * 地区编号
     */
    private String areaCode;

    /**
     * 最后离开时间
     */
    private Long lastOffTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    /**
     * 是否在线
     */
    private Integer onlineType;

    public Integer getOnlineType() {
        if (lastLoginTime != null && (lastOffTime == null || lastLoginTime.getTime() > lastOffTime)) {
            return Constants.ONE;
        } else {
            return Constants.ZERO;
        }
    }

    @Override
    public String toString() {
        return "用户id:" + (userId == null ? "空" : userId) + "，邮箱:" + (email == null ? "空" : email) + "，昵称:" + (nickName == null ? "空" : nickName) + "，添加好友方式: 0:直接添加,1:同意后添加:" + (joinType == null ? "空" : joinType) + "，性别:0男1女:" + (sex == null ? "空" : sex) + "，密码:" + "***" + "，个性签名:" + (personalSignature == null ? "空" : personalSignature) + "，状态:" + (status == null ? "空" : status) + "，创建时间:" + (createTime == null ? "空" : DateUtils.format(createTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "，最后登录时间:" + (lastLoginTime == null ? "空" : DateUtils.format(lastLoginTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "，地区:" + (areaName == null ? "空" : areaName) + "，地区编号:" + (areaCode == null ? "空" : areaCode) + "，最后离开时间:" + (lastOffTime == null ? "空" : lastOffTime) + "，是否删除:" + (isDelete == null ? "空" : isDelete);
    }
}
