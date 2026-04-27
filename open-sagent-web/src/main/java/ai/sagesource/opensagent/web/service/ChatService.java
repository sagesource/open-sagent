package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.core.agent.Agent;
import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.memory.Memory;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.completion.StreamChunk;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.ReActAgent;
import ai.sagesource.opensagent.infrastructure.agent.SimpleAgent;
import ai.sagesource.opensagent.infrastructure.agent.memory.MultipleSQLLiteMemory;
import ai.sagesource.opensagent.web.entity.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 对话执行业务逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LLMCompletion llmCompletion;
    private final ConversationService conversationService;
    private final TitleAgentService titleAgentService;

    @Qualifier("simpleAgentConfig")
    private final AgentConfig simpleAgentConfig;

    @Qualifier("smartAgentConfig")
    private final AgentConfig smartAgentConfig;

    private static final PromptTemplate DEFAULT_PROMPT = new PromptTemplate() {
        @Override
        public String render(PromptRenderContext context) {
            return "你是一个 helpful 的AI助手，请尽力回答用户的问题。";
        }

        @Override
        public String getRawContent() {
            return "你是一个 helpful 的AI助手，请尽力回答用户的问题。";
        }
    };

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

        conversationService.saveMessage(conversation.getId(), "user", message);

        boolean needTitle = false;
        long messageCount = conversationService.getMessages(conversation.getId()).size();
        if (messageCount <= 5) {
            needTitle = true;
        }

        Memory memory = new MultipleSQLLiteMemory(
                sessionId,
                "./memory.db",
                50,
                llmCompletion,
                null
        );

        Agent agent = createAgent(agentVersion, memory);

        final boolean finalNeedTitle = needTitle;

        Consumer<StreamChunk> chunkConsumer = chunk -> {
            try {
                if (chunk.getDeltaText() != null && !chunk.getDeltaText().isEmpty()) {
                    if (chunk.getDeltaText().contains(ACTION_PREFIX)) {
                        emitter.send(SseEmitter.event()
                                .name("action")
                                .data(chunk.getDeltaText()));
                    } else {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk.getDeltaText()));
                    }
                }
                if (chunk.isFinished()) {
                    if (finalNeedTitle) {
                        String title = titleAgentService.generateTitle(message, messageCount <= 1);
                        if (!"新对话".equals(title)) {
                            conversationService.updateTitle(userId, conversation.getId(), title);
                        }
                        emitter.send(SseEmitter.event()
                                .name("title")
                                .data(title));
                    }
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(""));
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("> ChatService | SSE发送失败: {} <", e.getMessage());
                emitter.completeWithError(e);
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
                log.error("> ChatService | 流式对话异常: {} <", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
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
                    DEFAULT_PROMPT,
                    null,
                    memory,
                    llmCompletion,
                    null,
                    smartAgentConfig
            );
        }
        return new SimpleAgent(
                "Sagent-Simple",
                DEFAULT_PROMPT,
                null,
                memory,
                llmCompletion,
                null,
                simpleAgentConfig
        );
    }
}
