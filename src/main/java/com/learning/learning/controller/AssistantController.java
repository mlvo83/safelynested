package com.learning.learning.controller;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.learning.learning.config.AssistantProperties;
import com.learning.learning.dto.AssistantChatRequest;
import com.learning.learning.service.AssistantRateLimiter;
import com.learning.learning.service.AssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP surface of the in-app help assistant. {@code POST /assistant/chat} streams the
 * reply as Server-Sent Events:
 * <ul>
 *   <li>{@code delta} - {"text": "..."} chunks of the visible answer</li>
 *   <li>{@code done}  - {"stopReason": "end_turn" | "max_tokens" | "refusal"}</li>
 *   <li>{@code error} - {"message": "..."} a user-readable problem (then the stream ends)</li>
 * </ul>
 * Login is required (SecurityConfig: {@code /assistant/**} is authenticated) and the POST
 * carries the usual CSRF header, which the widget reads from the XSRF-TOKEN cookie.
 */
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private AssistantRateLimiter rateLimiter;

    @Autowired
    private AssistantProperties properties;

    @Autowired
    @Qualifier("assistantExecutor")
    private Executor executor;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", assistantService.isEnabled());
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AssistantChatRequest request) {
        long timeoutMs = (properties.getTimeoutSeconds() + 15) * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(e -> open.set(false));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
                ? null : auth.getName();
        String message = request == null || request.getMessage() == null ? "" : request.getMessage().trim();

        String refusal = null;
        if (username == null) {
            refusal = "Please sign in to use the help assistant.";
        } else if (!assistantService.isEnabled()) {
            refusal = "The help assistant isn't switched on for this site yet. The written guides are at /help/.";
        } else if (message.isEmpty()) {
            refusal = "Please type a question first.";
        } else if (message.length() > properties.getMaxMessageChars()) {
            refusal = "That question is too long. Please keep it under " + properties.getMaxMessageChars() + " characters.";
        } else {
            refusal = rateLimiter.tryAcquire(username).orElse(null);
        }
        if (refusal != null) {
            fail(emitter, open, refusal);
            return emitter;
        }

        final String user = username;
        try {
            executor.execute(() -> run(emitter, open, user, request, message));
        } catch (RejectedExecutionException e) {
            log.warn("Help assistant: executor queue full, refusing request for {}", user);
            fail(emitter, open, "The assistant is very busy right now. Please try again in a minute.");
        }
        return emitter;
    }

    private void run(SseEmitter emitter, AtomicBoolean open, String user, AssistantChatRequest request, String message) {
        try {
            AssistantService.Result result = assistantService.streamReply(
                    user, request.getHistory(), message,
                    delta -> send(emitter, open, "delta", Map.of("text", delta)));

            if ("refusal".equals(result.stopReason())) {
                send(emitter, open, "delta", Map.of("text",
                        "\n\nI can't help with that one. I'm here for questions about using SafelyNested."));
            } else if ("max_tokens".equals(result.stopReason())) {
                send(emitter, open, "delta", Map.of("text",
                        "\n\n(That answer was cut short. Ask me to continue if you need the rest.)"));
            }
            send(emitter, open, "done", Map.of("stopReason", result.stopReason()));
            complete(emitter, open);
        } catch (ClientGoneException e) {
            log.debug("Help assistant: client disconnected mid-reply (user={})", user);
        } catch (RateLimitException e) {
            log.warn("Help assistant: Anthropic rate limit hit: {}", e.getMessage());
            fail(emitter, open, "The assistant is very busy right now. Please try again in a minute.");
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.error("Help assistant: API key rejected by Anthropic. Check ANTHROPIC_API_KEY. {}", e.getMessage());
            fail(emitter, open, "The help assistant isn't configured correctly. Please let an administrator know.");
        } catch (AnthropicServiceException e) {
            log.error("Help assistant: API error: {}", e.getMessage());
            fail(emitter, open, "The assistant hit a problem answering that. Please try again.");
        } catch (AnthropicIoException e) {
            log.error("Help assistant: network error reaching Anthropic: {}", e.getMessage());
            fail(emitter, open, "I couldn't reach the assistant service. Please try again in a moment.");
        } catch (Exception e) {
            log.error("Help assistant: unexpected error", e);
            fail(emitter, open, "Something went wrong. Please try again.");
        }
    }

    /** Sends one SSE event; converts a dead connection into {@link ClientGoneException} so the stream stops. */
    private void send(SseEmitter emitter, AtomicBoolean open, String event, Object data) {
        if (!open.get()) {
            throw new ClientGoneException();
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            open.set(false);
            throw new ClientGoneException();
        }
    }

    private void fail(SseEmitter emitter, AtomicBoolean open, String userMessage) {
        try {
            send(emitter, open, "error", Map.of("message", userMessage));
        } catch (ClientGoneException ignored) {
            // nothing to tell a client that already left
        }
        complete(emitter, open);
    }

    private void complete(SseEmitter emitter, AtomicBoolean open) {
        if (open.compareAndSet(true, false)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed or connection gone
            }
        }
    }

    private static class ClientGoneException extends RuntimeException {
        ClientGoneException() {
            super("client disconnected", null, false, false);
        }
    }
}
