package top.enderherman.wetalk.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import top.enderherman.wetalk.constants.Constants;

import java.io.Serializable;


@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysSettingDto implements Serializable {
    private Integer maxGroupCount = 5;
    private Integer maxGroupMemberCount = 500;
    private Integer maxImageSize = 200;
    private Integer maxVideoSize = 500;
    private Integer maxFileSize = 5000;
    private String robotUid = Constants.ROBOT_UID;
    private String robotNickName = "WeTalk Robot";
    private String robotWelcome = "欢迎使用WeTalk Robot!";


}
