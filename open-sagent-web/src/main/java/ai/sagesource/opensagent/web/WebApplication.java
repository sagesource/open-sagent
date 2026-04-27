package ai.sagesource.opensagent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web模块启动类
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@SpringBootApplication(scanBasePackages = {
        "ai.sagesource.opensagent.web",
        "ai.sagesource.opensagent.infrastructure"
})
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
