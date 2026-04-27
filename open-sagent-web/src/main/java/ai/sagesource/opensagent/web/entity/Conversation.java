package ai.sagesource.opensagent.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话实体
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属用户ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 会话唯一标识（用于MultipleSQLLiteMemory的sessionId）
     */
    @Column(unique = true, nullable = false, length = 64)
    private String sessionId;

    /**
     * 对话标题
     */
    @Column(length = 256)
    private String title;

    /**
     * Agent版本：simple / smart
     */
    @Column(nullable = false, length = 16)
    private String agentVersion;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
