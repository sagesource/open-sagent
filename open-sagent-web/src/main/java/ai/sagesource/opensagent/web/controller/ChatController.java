package ai.sagesource.opensagent.web.controller;

import ai.sagesource.opensagent.web.security.JwtInterceptor;
import ai.sagesource.opensagent.web.service.ChatService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE流式对话接口控制器
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamChat(
            HttpServletRequest request,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("message") String message,
            @RequestParam(value = "agentVersion", defaultValue = "simple") String agentVersion) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        return chatService.streamChat(userId, sessionId, message, agentVersion);
    }

    @PostMapping("/{sessionId}/cancel")
    public void cancelChat(@PathVariable("sessionId") String sessionId) {
        chatService.cancelChat(sessionId);
    }
}
