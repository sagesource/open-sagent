package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

/**
 * CompletionMessage JSON 序列化工具
 * <p>
 * 基于 fastjson2 的 autoType 特性，支持 CompletionMessage 多态子类的完整序列化与反序列化。
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
public class MessageJsonUtils {

    /**
     * 将 CompletionMessage 序列化为 JSON 字符串
     *
     * @param message 消息对象
     * @return JSON 字符串（包含 @type 类型信息）
     */
    public static String toJson(CompletionMessage message) {
        return JSON.toJSONString(message, JSONWriter.Feature.WriteClassName);
    }

    /**
     * 将 JSON 字符串反序列化为 CompletionMessage
     *
     * @param json JSON 字符串
     * @return CompletionMessage 对象
     */
    public static CompletionMessage fromJson(String json) {
        return JSON.parseObject(json, CompletionMessage.class, JSONReader.Feature.SupportAutoType);
    }
}
