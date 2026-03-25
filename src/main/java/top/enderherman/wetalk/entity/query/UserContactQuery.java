package top.enderherman.wetalk.entity.query;


/**
 * 联系人表参数
 */
public class UserContactQuery extends BaseParam {


    /**
     * 用户id
     */
    private String userId;

    private String userIdFuzzy;

    /**
     * 联系人id或者群组id
     */
    private String contactId;

    private String contactIdFuzzy;

    /**
     * 联系人类型: 0:好友 1:群组
     */
    private Integer contactType;

    /**
     * 创建时间
     */
    private String createTime;

    private String createTimeStart;

    private String createTimeEnd;

    /**
     * 状态: 0:非好友 1:好友 2:已删除好友 3:被好友删除 4:已拉黑好友 5:被好友拉黑
     */
    private Integer status;

    /**
     * 最后更新时间
     */
    private String updateTime;

    private String updateTimeStart;

    private String updateTimeEnd;

    /**
     * 是否联合查询用户名称
     */
    private Boolean queryUserInfo;
    /**
     * 是否联合查询联系人名称
     */
    private Boolean queryContactInfo;
    /**
     * 是否联合查询群组名称
     */
    private Boolean queryGroupInfo;
    /**
     * 是否联合查询时排除我的群组
     */
    private Boolean queryExcludeMyGroup;

    /**
     * 查询状态的数组
     */
    private Integer[] statusArray;
    /**
     * 是否查询群成员
     */
    private Boolean queryMemberCount;


    public Boolean getQueryContactInfo() {
        return queryContactInfo;
    }

    public void setQueryContactInfo(Boolean queryContactInfo) {
        this.queryContactInfo = queryContactInfo;
    }

    public Boolean getQueryGroupInfo() {
        return queryGroupInfo;
    }

    public void setQueryGroupInfo(Boolean queryGroupInfo) {
        this.queryGroupInfo = queryGroupInfo;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserIdFuzzy(String userIdFuzzy) {
        this.userIdFuzzy = userIdFuzzy;
    }

    public String getUserIdFuzzy() {
        return this.userIdFuzzy;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getContactId() {
        return this.contactId;
    }

    public void setContactIdFuzzy(String contactIdFuzzy) {
        this.contactIdFuzzy = contactIdFuzzy;
    }

    public String getContactIdFuzzy() {
        return this.contactIdFuzzy;
    }

    public void setContactType(Integer contactType) {
        this.contactType = contactType;
    }

    public Integer getContactType() {
        return this.contactType;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getCreateTime() {
        return this.createTime;
    }

    public void setCreateTimeStart(String createTimeStart) {
        this.createTimeStart = createTimeStart;
    }

    public String getCreateTimeStart() {
        return this.createTimeStart;
    }

    public void setCreateTimeEnd(String createTimeEnd) {
        this.createTimeEnd = createTimeEnd;
    }

    public String getCreateTimeEnd() {
        return this.createTimeEnd;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getStatus() {
        return this.status;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTimeStart(String updateTimeStart) {
        this.updateTimeStart = updateTimeStart;
    }

    public String getUpdateTimeStart() {
        return this.updateTimeStart;
    }

    public void setUpdateTimeEnd(String updateTimeEnd) {
        this.updateTimeEnd = updateTimeEnd;
    }

    public String getUpdateTimeEnd() {
        return this.updateTimeEnd;
    }

    public Boolean getQueryUserInfo() {
        return queryUserInfo;
    }

    public void setQueryUserInfo(Boolean queryUserInfo) {
        this.queryUserInfo = queryUserInfo;
    }

    public Boolean getQueryExcludeMyGroup() {
        return queryExcludeMyGroup;
    }

    public void setQueryExcludeMyGroup(Boolean queryExcludeMyGroup) {
        this.queryExcludeMyGroup = queryExcludeMyGroup;
    }

    public Integer[] getStatusArray() {
        return statusArray;
    }

    public void setStatusArray(Integer[] statusArray) {
        this.statusArray = statusArray;
    }

    public Boolean getQueryMemberCount() {
        return queryMemberCount;
    }

    public void setQueryMemberCount(Boolean queryMemberCount) {
        this.queryMemberCount = queryMemberCount;
    }
}
