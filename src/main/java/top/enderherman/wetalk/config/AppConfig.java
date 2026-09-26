package top.enderherman.wetalk.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



@Data
@Component("appConfig")
public class AppConfig {

    /**
     * webSocket端口
     */
    @Value("${ws.port:}")
    private Integer wsPost;

    /**
     * 文件目录
     */
    @Value("${project.folder:}")
    private String projectFolder;

    public String getProjectFolder() {
        return java.nio.file.Path.of(projectFolder).toAbsolutePath().normalize()
                + java.io.File.separator;
    }

    /**
     * 管理员
     */
    @Value("${admin.emails:}")
    private String adminEmails;
}
