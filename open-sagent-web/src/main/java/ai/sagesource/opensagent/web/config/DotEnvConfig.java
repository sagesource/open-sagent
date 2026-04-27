package ai.sagesource.opensagent.web.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * DotEnv配置加载
 * <p>
 * 在Spring环境初始化前加载.env文件，将变量注入System Properties
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Configuration
public class DotEnvConfig {

    @PostConstruct
    public void init() {
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
            log.info("> DotEnvConfig | .env文件加载完成 <");
        } catch (Exception e) {
            log.warn("> DotEnvConfig | .env文件加载失败（可忽略）: {} <", e.getMessage());
        }
    }
}
