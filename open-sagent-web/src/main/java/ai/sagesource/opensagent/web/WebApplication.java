package ai.sagesource.opensagent.web;

import io.github.cdimascio.dotenv.Dotenv;
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
        loadDotEnv();
        SpringApplication.run(WebApplication.class, args);
    }

    private static void loadDotEnv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> {
                if (System.getProperty(entry.getKey()) == null
                        && System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            // .env 文件可选，加载失败可忽略
        }
    }
}
