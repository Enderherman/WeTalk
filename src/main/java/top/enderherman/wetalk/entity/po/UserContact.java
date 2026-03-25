package top.enderherman.wetalk.entity.po;


import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import top.enderherman.wetalk.entity.enums.DateTimePatternEnum;
import top.enderherman.wetalk.utils.DateUtils;

import java.io.Serializable;
import java.util.Date;


/**
 * 联系人表
 */
public class UserContact implements Serializable {


    /**
     * 用户id
     */
    private String userId;

    /**
     * 联系人id或者群组id
     */
    private String contactId;

    /**
     * 联系人类型: 0:好友 1:群组
     */
    private Integer contactType;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 状态: 0:非好友 1:好友 2:已删除好友 3:被好友删除 4:已拉黑好友 5:被好友拉黑
     */
    private Integer status;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 联系人名称
     */
    private String contactName;

    /**
     * 性别
     */
    private Integer sex;

    /**
     * 如果是群聊 可以查询群聊人数
     */
    private Integer memberCount;

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getContactId() {
        return this.contactId;
    }

    public void setContactType(Integer contactType) {
        this.contactType = contactType;
    }

    public Integer getContactType() {
        return this.contactType;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getCreateTime() {
        return this.createTime;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Date getUpdateTime() {
        return this.updateTime;
    }

    @Override
    public String toString() {
        return "用户id:" + (userId == null ? "空" : userId) + "，联系人id或者群组id:" + (contactId == null ? "空" : contactId) + "，联系人类型: 0:好友 1:群组:" + (contactType == null ? "空" : contactType) + "，创建时间:" + (createTime == null ? "空" : DateUtils.format(createTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "，状态: 0:非好友 1:好友 2:已删除好友 3:被好友删除 4:已拉黑好友 5:被好友拉黑:" + (status == null ? "空" : status) + "，最后更新时间:" + (updateTime == null ? "空" : DateUtils.format(updateTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern()));
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }
}
