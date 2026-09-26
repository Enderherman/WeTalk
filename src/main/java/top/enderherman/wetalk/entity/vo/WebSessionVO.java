package top.enderherman.wetalk.entity.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class WebSessionVO implements Serializable {
    private String userId;
    private String email;
    private String nickName;
    private Boolean admin;
}
