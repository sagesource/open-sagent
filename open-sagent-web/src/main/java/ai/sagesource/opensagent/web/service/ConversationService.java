package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.web.entity.ChatMessage;
import ai.sagesource.opensagent.web.entity.Conversation;
import ai.sagesource.opensagent.web.repository.ConversationRepository;
import ai.sagesource.opensagent.web.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 对话管理业务逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 获取用户的对话列表
     */
    public List<Conversation> listConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 创建新对话
     */
    @Transactional
    public Conversation createConversation(Long userId, String agentVersion) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .sessionId(sessionId)
                .title("新对话")
                .agentVersion(agentVersion != null ? agentVersion : "simple")
                .build();
        return conversationRepository.save(conversation);
    }

    /**
     * 删除对话
     */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此对话");
        }
        messageRepository.deleteAll(
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));
        conversationRepository.delete(conversation);
    }

    /**
     * 更新对话标题
     */
    public Conversation updateTitle(Long userId, Long conversationId, String title) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此对话");
        }
        conversation.setTitle(title);
        return conversationRepository.save(conversation);
    }

    /**
     * 根据sessionId获取对话
     */
    public Conversation getBySessionId(String sessionId) {
        return conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
    }

    /**
     * 保存消息到前端消息表
     */
    @Transactional
    public void saveMessage(Long conversationId, String role, String content) {
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    /**
     * 获取对话的消息列表
     */
    public List<ChatMessage> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
