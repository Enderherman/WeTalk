package top.enderherman.wetalk.entity.query;


/**
 * 群组信息表参数
 */
public class GroupInfoQuery extends BaseParam {


	/**
	 * 群组id
	 */
	private String groupId;

	private String groupIdFuzzy;

	/**
	 * 群组昵称
	 */
	private String groupName;

	private String groupNameFuzzy;

	/**
	 * 群主用户id
	 */
	private String groupOwnId;

	private String groupOwnIdFuzzy;

	/**
	 * 创建时间
	 */
	private String createTime;

	private String createTimeStart;

	private String createTimeEnd;

	/**
	 * 创建时间
	 */
	private String groupNotice;

	private String groupNoticeFuzzy;

	/**
	 * 加入方式: 0:直接加入 1:管理员同意后加入
	 */
	private Integer joinType;

	/**
	 * 状态： 0:解散 1:正常
	 */
	private Integer status;

	/**
	 * 是否查询群主名称
	 */
	private Boolean queryGroupOwnerName;


	/**
	 * 是否查询群成员
	 */
	private Boolean queryMemberCount;

	public Boolean getQueryGroupOwnerName() {
		return queryGroupOwnerName;
	}

	public void setQueryGroupOwnerName(Boolean queryGroupOwnerName) {
		this.queryGroupOwnerName = queryGroupOwnerName;
	}

	public Boolean getQueryMemberCount() {
		return queryMemberCount;
	}

	public void setQueryMemberCount(Boolean queryMemberCount) {
		this.queryMemberCount = queryMemberCount;
	}

	public void setGroupId(String groupId){
		this.groupId = groupId;
	}

	public String getGroupId(){
		return this.groupId;
	}

	public void setGroupIdFuzzy(String groupIdFuzzy){
		this.groupIdFuzzy = groupIdFuzzy;
	}

	public String getGroupIdFuzzy(){
		return this.groupIdFuzzy;
	}

	public void setGroupName(String groupName){
		this.groupName = groupName;
	}

	public String getGroupName(){
		return this.groupName;
	}

	public void setGroupNameFuzzy(String groupNameFuzzy){
		this.groupNameFuzzy = groupNameFuzzy;
	}

	public String getGroupNameFuzzy(){
		return this.groupNameFuzzy;
	}

	public void setGroupOwnId(String groupOwnId){
		this.groupOwnId = groupOwnId;
	}

	public String getGroupOwnId(){
		return this.groupOwnId;
	}

	public void setGroupOwnIdFuzzy(String groupOwnIdFuzzy){
		this.groupOwnIdFuzzy = groupOwnIdFuzzy;
	}

	public String getGroupOwnIdFuzzy(){
		return this.groupOwnIdFuzzy;
	}

	public void setCreateTime(String createTime){
		this.createTime = createTime;
	}

	public String getCreateTime(){
		return this.createTime;
	}

	public void setCreateTimeStart(String createTimeStart){
		this.createTimeStart = createTimeStart;
	}

	public String getCreateTimeStart(){
		return this.createTimeStart;
	}
	public void setCreateTimeEnd(String createTimeEnd){
		this.createTimeEnd = createTimeEnd;
	}

	public String getCreateTimeEnd(){
		return this.createTimeEnd;
	}

	public void setGroupNotice(String groupNotice){
		this.groupNotice = groupNotice;
	}

	public String getGroupNotice(){
		return this.groupNotice;
	}

	public void setGroupNoticeFuzzy(String groupNoticeFuzzy){
		this.groupNoticeFuzzy = groupNoticeFuzzy;
	}

	public String getGroupNoticeFuzzy(){
		return this.groupNoticeFuzzy;
	}

	public void setJoinType(Integer joinType){
		this.joinType = joinType;
	}

	public Integer getJoinType(){
		return this.joinType;
	}

	public void setStatus(Integer status){
		this.status = status;
	}

	public Integer getStatus(){
		return this.status;
	}

}
