package top.enderherman.wetalk.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${java.io.tmpdir}")
    private String tmpdir;

    /**
     * 自定义MultipartResolver
     */
    @Bean
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        return resolver;
    }

    /**
     * 自定义MultipartConfigElement
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 确保临时目录存在
        File tmp = new File(tmpdir);
        if (!tmp.exists()) {
            tmp.mkdirs();
        }
        
        factory.setLocation(tmpdir);
        factory.setMaxFileSize(DataSize.ofMegabytes(500));
        factory.setMaxRequestSize(DataSize.ofMegabytes(500));
        
        return factory.createMultipartConfig();
    }
} 