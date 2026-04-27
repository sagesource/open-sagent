package ai.sagesource.opensagent.web.controller;

import ai.sagesource.opensagent.web.dto.*;
import ai.sagesource.opensagent.web.entity.ChatMessage;
import ai.sagesource.opensagent.web.entity.Conversation;
import ai.sagesource.opensagent.web.security.JwtInterceptor;
import ai.sagesource.opensagent.web.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话管理接口控制器
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ApiResponse<List<ConversationDTO>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        List<Conversation> list = conversationService.listConversations(userId);
        return ApiResponse.success(list.stream()
                .map(c -> ConversationDTO.builder()
                        .id(c.getId())
                        .sessionId(c.getSessionId())
                        .title(c.getTitle())
                        .agentVersion(c.getAgentVersion())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ApiResponse<ConversationDTO> create(
            HttpServletRequest request,
            @RequestBody CreateConversationRequest body) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        Conversation c = conversationService.createConversation(userId, body.getAgentVersion());
        return ApiResponse.success(ConversationDTO.builder()
                .id(c.getId())
                .sessionId(c.getSessionId())
                .title(c.getTitle())
                .agentVersion(c.getAgentVersion())
                .updatedAt(c.getUpdatedAt())
                .build());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        conversationService.deleteConversation(userId, id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/title")
    public ApiResponse<Void> updateTitle(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody UpdateTitleRequest body) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        conversationService.updateTitle(userId, id, body.getTitle());
        return ApiResponse.success();
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageDTO>> getMessages(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        List<ChatMessage> messages = conversationService.getMessages(id);
        return ApiResponse.success(messages.stream()
                .map(m -> MessageDTO.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList()));
    }
}
