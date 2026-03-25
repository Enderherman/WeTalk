package top.enderherman.wetalk.entity.vo;



import lombok.Data;

import java.io.Serializable;

@Data
public class UserInfoVO implements Serializable {

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
     * 个性签名
     */
    private String personalSignature;

    /**
     * 地区
     */
    private String areaName;

    /**
     * 地区编号
     */
    private String areaCode;


    /**
     * token
     */
    private String token;

    /**
     * 是否为管理员
     */
    private Boolean admin;

    /**
     * 联系人状态
     */
    private Integer contactStatus;


}
