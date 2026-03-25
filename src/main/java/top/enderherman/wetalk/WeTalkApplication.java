package top.enderherman.wetalk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"top.enderherman.wetalk"})
@MapperScan(basePackages = {"top.enderherman.wetalk.mappers"})
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class WeTalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeTalkApplication.class, args);
    }

}
