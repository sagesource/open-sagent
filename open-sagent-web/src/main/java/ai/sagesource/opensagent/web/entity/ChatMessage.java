package ai.sagesource.opensagent.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体（用于前端展示的消息历史）
 * <p>
 * 注：Agent的Memory使用MultipleSQLLiteMemory独立存储，此表仅用于前端快速加载消息列表
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属对话ID
     */
    @Column(nullable = false)
    private Long conversationId;

    /**
     * 消息角色：user / assistant / tool
     */
    @Column(nullable = false, length = 16)
    private String role;

    /**
     * 消息内容
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
