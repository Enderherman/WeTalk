package top.enderherman.wetalk.entity.vo;

import lombok.Data;
import top.enderherman.wetalk.entity.enums.UserContactStatusEnum;

import java.io.Serializable;

@Data
public class UserContactSearchResultVO implements Serializable {


    private String contactId;

    private String contactType;

    private String nickName;

    private Integer status;

    private String statusName;

    private Integer sex;

    private String areaName;

    public String getStatusName() {
        UserContactStatusEnum statusEnum = UserContactStatusEnum.getByStatus(status);
        return statusEnum == null ? null : statusEnum.getDescription();
    }


}
