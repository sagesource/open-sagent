package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.core.agent.Agent;
import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.memory.Memory;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.completion.StreamChunk;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.ReActAgent;
import ai.sagesource.opensagent.infrastructure.agent.SimpleAgent;
import ai.sagesource.opensagent.infrastructure.agent.memory.MultipleSQLLiteMemory;
import ai.sagesource.opensagent.web.entity.Conversation;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 对话执行业务逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Service
public class ChatService {

    @Resource
    @Qualifier("simpleCompletion")
    private LLMCompletion simpleCompletion;

    @Resource
    @Qualifier("smartCompletion")
    private LLMCompletion smartCompletion;

    @Resource
    @Qualifier("simplePromptTemplate")
    private PromptTemplate simplePromptTemplate;

    @Resource
    @Qualifier("smartPromptTemplate")
    private PromptTemplate smartPromptTemplate;

    @Resource
    private ConversationService conversationService;

    @Resource
    private TitleAgentService titleAgentService;

    @Resource
    @Qualifier("simpleAgentConfig")
    private AgentConfig simpleAgentConfig;

    @Resource
    @Qualifier("smartAgentConfig")
    private AgentConfig smartAgentConfig;

    @Value("${sagent.memory.db-path:./memory.db}")
    private String memoryDbPath;

    private static final String ACTION_PREFIX = "AGENT_ACTION[";

    /**
     * 活跃的SSE连接取消令牌映射
     */
    private final Map<String, CompletionCancelToken> activeTokens = new ConcurrentHashMap<>();

    /**
     * 执行SSE流式对话
     */
    public SseEmitter streamChat(Long userId, String sessionId, String message, String agentVersion) {
        Conversation conversation = conversationService.getBySessionId(sessionId);
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问此对话");
        }

        SseEmitter emitter = new SseEmitter(0L);
        String emitterId = sessionId + "-" + System.currentTimeMillis();
        AtomicBoolean emitterBroken = new AtomicBoolean(false);

        conversationService.saveMessage(conversation.getId(), "user", message);

        boolean needTitle = false;
        long messageCount = conversationService.getMessages(conversation.getId()).size();
        if (messageCount <= 5) {
            needTitle = true;
        }

        LLMCompletion memoryCompletion = "smart".equals(agentVersion) ? smartCompletion : simpleCompletion;
        Memory memory = new MultipleSQLLiteMemory(
                sessionId,
                memoryDbPath,
                50,
                memoryCompletion,
                null
        );

        Agent agent = createAgent(agentVersion, memory);

        final boolean finalNeedTitle = needTitle;
        final StringBuilder assistantResponse = new StringBuilder();

        Consumer<StreamChunk> chunkConsumer = chunk -> {
            if (emitterBroken.get()) {
                return;
            }
            try {
                if (chunk.getDeltaText() != null && !chunk.getDeltaText().isEmpty()) {
                    if (chunk.getDeltaText().contains(ACTION_PREFIX)) {
                        emitter.send(SseEmitter.event()
                                .name("action")
                                .data(JSON.toJSONString(chunk.getDeltaText())));
                    } else {
                        assistantResponse.append(chunk.getDeltaText());
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(JSON.toJSONString(chunk.getDeltaText())));
                    }
                }
                if (chunk.isFinished()) {
                    if (assistantResponse.length() > 0) {
                        conversationService.saveMessage(
                                conversation.getId(),
                                "assistant",
                                assistantResponse.toString()
                        );
                    }
                    if (finalNeedTitle) {
                        String title = titleAgentService.generateTitle(message, messageCount <= 1);
                        if (!"新对话".equals(title)) {
                            conversationService.updateTitle(userId, conversation.getId(), title);
                        }
                        emitter.send(SseEmitter.event()
                                .name("title")
                                .data(JSON.toJSONString(title)));
                    }
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(JSON.toJSONString("")));
                    emitter.complete();
                }
            } catch (Exception e) {
                emitterBroken.set(true);
                log.warn("> ChatService | SSE连接已断开，停止发送，sessionId: {} <", sessionId);
            }
        };

        new Thread(() -> {
            try {
                CompletionCancelToken token = agent.stream(
                        UserCompletionMessage.of(message),
                        chunkConsumer
                );
                activeTokens.put(emitterId, token);
            } catch (Exception e) {
                if (emitterBroken.get()) {
                    log.warn("> ChatService | 客户端已断开，忽略流式异常，sessionId: {} <", sessionId);
                    return;
                }
                log.error("> ChatService | 流式对话异常 <", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(JSON.toJSONString(e.getMessage())));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("> ChatService | SSE连接已断开，无法发送错误事件 <");
                }
            } finally {
                activeTokens.remove(emitterId);
            }
        }).start();

        emitter.onCompletion(() -> activeTokens.remove(emitterId));
        emitter.onTimeout(() -> {
            activeTokens.remove(emitterId);
            emitter.complete();
        });
        emitter.onError((e) -> activeTokens.remove(emitterId));

        return emitter;
    }

    /**
     * 中断对话
     */
    public void cancelChat(String sessionId) {
        activeTokens.forEach((key, token) -> {
            if (key.startsWith(sessionId)) {
                token.cancel();
            }
        });
    }

    private Agent createAgent(String version, Memory memory) {
        if ("smart".equals(version)) {
            return new ReActAgent(
                    "Sagent-Smart",
                    smartPromptTemplate,
                    null,
                    memory,
                    smartCompletion,
                    null,
                    smartAgentConfig
            );
        }
        return new SimpleAgent(
                "Sagent-Simple",
                simplePromptTemplate,
                null,
                memory,
                simpleCompletion,
                null,
                simpleAgentConfig
        );
    }
}
