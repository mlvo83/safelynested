package com.learning.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for the in-app Claude help assistant, bound from {@code app.assistant.*} in
 * {@code application.properties}. The feature is live only when an API key is present
 * ({@link #isEnabled()}); everything else has a sensible default.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    /** Anthropic API key ({@code ANTHROPIC_API_KEY}). Blank = assistant disabled. */
    private String apiKey = "";

    /**
     * Optional Console workspace id ({@code ANTHROPIC_WORKSPACE_ID}, looks like {@code wrkspc_...}).
     * Needed only when the API key is an organization-level key; a workspace-scoped key leaves this blank.
     */
    private String workspaceId = "";

    /** Model id, e.g. {@code claude-opus-5-5}, {@code claude-sonnet-5-5}, {@code claude-haiku-4-5}. */
    private String model = "claude-opus-5-5";

    /** Thinking effort: low | medium | high. Blank omits the parameter. */
    private String effort = "low";

    /** Max tokens the model may generate per reply (thinking + visible answer). */
    private int maxOutputTokens = 2048;

    /** Longest single user message accepted, in characters. */
    private int maxMessageChars = 2000;

    /** Longest single history turn kept, in characters (longer turns are truncated). */
    private int maxHistoryTurnChars = 4000;

    /** How many prior turns are replayed to the model. */
    private int maxHistoryMessages = 20;

    /** Per-user requests allowed per rolling minute. */
    private int perMinuteLimit = 10;

    /** Per-user requests allowed per rolling day. */
    private int perDayLimit = 150;

    /** Seconds to wait for the model before the request is abandoned. */
    private int timeoutSeconds = 90;

    /** Include the public onboarding guides (served under /help/) in the system prompt. */
    private boolean includeOnboardingDocs = true;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
