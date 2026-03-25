package top.enderherman.wetalk.entity.vo;



import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AppUpdateVO implements Serializable {

    private Integer id;

    /**
     * 版本号
     */
    private String version;

    /**
     * 更新描述
     */
    private List<String> updateList;

    /**
     * 更新文件大小
     */
    private Long size;


    private String fileName;

    private Integer fileType;

    private String outerLink;

}
