package ai.sagesource.opensagent.web.repository;

import ai.sagesource.opensagent.web.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 对话数据访问接口
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Conversation> findBySessionId(String sessionId);

    boolean existsBySessionIdAndUserId(String sessionId, Long userId);
}
