package ai.sagesource.opensagent.base.utils;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * DotEnv工具类
 * <p>
 * 封装DotEnv配置读取，用于获取敏感配置如API密钥等
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class DotEnvUtils {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    /**
     * 获取环境变量值
     *
     * @param key 变量名
     * @return 变量值，不存在则返回null
     */
    public static String get(String key) {
        // 优先从系统环境变量获取
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // 从.env文件获取
        return dotenv.get(key);
    }

    /**
     * 获取环境变量值，带默认值
     *
     * @param key          变量名
     * @param defaultValue 默认值
     * @return 变量值，不存在则返回默认值
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
