package top.enderherman.wetalk.entity.po;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import top.enderherman.wetalk.entity.enums.DateTimePatternEnum;
import top.enderherman.wetalk.utils.DateUtils;
import top.enderherman.wetalk.utils.StringUtils;

import java.io.Serializable;
import java.util.Date;


/**
 * app发布表
 */
@Data
public class AppUpdate implements Serializable {


    /**
     * id
     */
    private Integer id;

    /**
     * 版本号
     */
    private String version;

    /**
     * 更新信息
     */
    private String updateDesc;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 0:未发布 1:灰度发布 2:全部发布
     */
    private Integer status;

    /**
     * 灰度uid
     */
    private String grayscaleUid;

    /**
     * 文件类型 0:本地文件 1:外链
     */
    private Integer fileType;

    /**
     * 外链地址
     */
    private String outerLink;

    private String[] updateDescArray;


    public String[] getUpdateDescArray() {
        if (!StringUtils.isEmpty(updateDesc)) {
            return updateDesc.split("\\|");
        }
        return updateDescArray;
    }

    @Override
    public String toString() {
        return "id:" + (id == null ? "空" : id) + "，版本号:" + (version == null ? "空" : version) + "，更新信息:" + (updateDesc == null ? "空" : updateDesc) + "，创建时间:" + (createTime == null ? "空" : DateUtils.format(createTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "，0:未发布 1:灰度发布 2:全部发布:" + (status == null ? "空" : status) + "，灰度uid:" + (grayscaleUid == null ? "空" : grayscaleUid) + "，文件类型 0:本地文件 1:外链:" + (fileType == null ? "空" : fileType) + "，外链地址:" + (outerLink == null ? "空" : outerLink);
    }
}
