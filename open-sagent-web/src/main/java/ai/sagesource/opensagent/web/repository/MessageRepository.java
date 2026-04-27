package ai.sagesource.opensagent.web.repository;

import ai.sagesource.opensagent.web.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问接口
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Repository
public interface MessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
