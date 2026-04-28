package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.core.agent.Agent;
import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.SimpleAgent;
import ai.sagesource.opensagent.infrastructure.agent.memory.MultipleSQLLiteMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 标题生成Agent服务
 * <p>
 * 基于SimpleAgent实现，根据对话内容生成简短标题
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Service
public class TitleAgentService {

    @Resource
    @Qualifier("titleCompletion")
    private LLMCompletion titleCompletion;

    @Resource
    @Qualifier("titlePromptTemplate")
    private PromptTemplate titlePromptTemplate;

    @Resource
    @Qualifier("titleAgentConfig")
    private AgentConfig titleAgentConfig;

    private static final String FIRST_TITLE = "新对话";

    /**
     * 生成对话标题
     * <p>
     * 如果是第一条消息，返回默认标题；否则调用标题Agent生成
     */
    public String generateTitle(String userMessage, boolean isFirstMessage) {
        if (isFirstMessage) {
            return FIRST_TITLE;
        }

        try {
            // 使用独立的Memory（sessionId加前缀避免冲突）
            MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory("title-agent-" + System.currentTimeMillis());

            Agent agent = new SimpleAgent(
                    "TitleAgent",
                    titlePromptTemplate,
                    null,
                    memory,
                    titleCompletion,
                    null,
                    titleAgentConfig
            );

            AgentResponse response = agent.chat(UserCompletionMessage.of(userMessage));
            String title = response.getMessage().getTextContent().trim();

            // 清理标题Agent的Memory（避免污染数据库）
            memory.clear();

            if (title.isEmpty()) {
                return FIRST_TITLE;
            }
            // 限制长度
            return title.length() > 20 ? title.substring(0, 20) : title;
        } catch (Exception e) {
            log.warn("> TitleAgentService | 标题生成失败: {} <", e.getMessage());
            return FIRST_TITLE;
        }
    }
}
