package com.learning.learning.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.learning.learning.config.AssistantProperties;
import com.learning.learning.dto.AssistantChatRequest;
import com.learning.learning.entity.Role;
import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Phase 1 of the in-app Claude help assistant: a read-only "how do I use the app" guide.
 * <p>
 * Each question becomes one streaming Messages API call made with the single server-side
 * key in {@link AssistantProperties}. The prompt is: a large, cached, user-independent
 * knowledge block from {@link AssistantKnowledgeService}; a small uncached block naming
 * the signed-in user's role and charity; the recent chat history; and the new question.
 * <p>
 * Nothing from the database other than the user's own name, role and charity name/id is
 * ever placed in a prompt. No family, referral, donor or financial data is sent, and the
 * assistant has no tools, so it cannot take actions.
 */
@Service("assistantService")
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final Map<String, String> ROLE_LABELS = Map.ofEntries(
            Map.entry("ROLE_ADMIN", "Administrator (full system access; home page /home)"),
            Map.entry("ROLE_FACILITATOR", "Facilitator for all charities (home page /facilitator/dashboard)"),
            Map.entry("ROLE_MULTI_FACILITATOR", "Multi-charity facilitator (home page /multi-facilitator/hub, then per-charity pages under /charity-facilitator/{charityId}/...)"),
            Map.entry("ROLE_CHARITY_FACILITATOR", "Charity facilitator for one charity (home page /charity-partner/dashboard; facilitation pages under /charity-facilitator/{charityId}/...)"),
            Map.entry("ROLE_CHARITY_PARTNER", "Charity partner for one charity (home page /charity-partner/dashboard)"),
            Map.entry("ROLE_DONOR", "Donor (home page /donor/dashboard)"),
            Map.entry("ROLE_LOCATION_ADMIN", "Location admin for all charities' shelter locations (home page /location-admin/dashboard)"),
            Map.entry("ROLE_LOCATION_PARTNER", "Location (stay) partner offering properties (home page /location-partner/dashboard)"),
            Map.entry("ROLE_USER", "Legacy user role (no pages of its own)"),
            Map.entry("ROLE_CLIENT", "Legacy client role (no pages of its own)")
    );

    @Autowired
    private AssistantProperties properties;

    @Autowired
    private AssistantKnowledgeService knowledge;

    @Autowired
    private UserRepository userRepository;

    /** Null when the assistant is disabled (no API key). */
    private AnthropicClient client;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Help assistant is OFF: ANTHROPIC_API_KEY is not set. The widget will not render.");
            return;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(1);
        boolean hasWorkspace = properties.getWorkspaceId() != null && !properties.getWorkspaceId().isBlank();
        if (hasWorkspace) {
            // An organization-level key must say which workspace to bill; a workspace-scoped key must not.
            builder.putHeader("anthropic-workspace-id", properties.getWorkspaceId().trim());
        }
        client = builder.build();
        log.info("Help assistant is ON: model={}, effort={}, maxOutputTokens={}, workspaceHeader={}, knowledge ~{} tokens",
                properties.getModel(), properties.getEffort(), properties.getMaxOutputTokens(), hasWorkspace,
                knowledge.getStaticSystemPrompt().length() / 4);
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    /** True when an API key is configured and the client was built. Used by the widget fragment. */
    public boolean isEnabled() {
        return client != null;
    }

    /** The shared API client, or null when disabled. Package-private for {@link AssistantTopicGate}. */
    AnthropicClient client() {
        return client;
    }

    /** Receives visible answer text as it streams in. May throw to abort the stream. */
    @FunctionalInterface
    public interface TextListener {
        void onText(String delta);
    }

    /** Outcome of one reply. {@code stopReason} is end_turn, max_tokens, refusal, or another API value. */
    public record Result(String stopReason, long inputTokens, long cacheReadTokens, long cacheWriteTokens, long outputTokens) {
    }

    /**
     * Streams a reply to {@code message} for the signed-in {@code username}. Blocks until
     * the model finishes, so callers run it on {@code assistantExecutor}, never on a
     * request thread. Exceptions from the SDK ({@code com.anthropic.errors.*}) propagate.
     */
    public Result streamReply(String username, List<AssistantChatRequest.Turn> history, String message, TextListener listener) {
        if (client == null) {
            throw new IllegalStateException("Help assistant is not enabled");
        }

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens((long) properties.getMaxOutputTokens())
                .systemOfTextBlockParams(List.of(
                        // Stable, identical for everyone: cached for an hour so repeat questions
                        // pay ~10% of the input price for this block.
                        TextBlockParam.builder()
                                .text(knowledge.getStaticSystemPrompt())
                                .cacheControl(CacheControlEphemeral.builder()
                                        .ttl(CacheControlEphemeral.Ttl.TTL_1H)
                                        .build())
                                .build(),
                        // Per-user: comes AFTER the cache breakpoint so it never invalidates it.
                        TextBlockParam.builder()
                                .text(buildUserContext(username))
                                .build()))
                // Opaque per-user id for Anthropic-side abuse detection; never the raw username.
                .metadata(Metadata.builder().userId(sha256(username)).build());

        effortFor(properties.getModel(), properties.getEffort())
                .ifPresent(effort -> params.outputConfig(OutputConfig.builder().effort(effort).build()));

        for (Turn turn : sanitize(history)) {
            params.addMessage(MessageParam.builder()
                    .role(turn.user ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .content(turn.text)
                    .build());
        }
        params.addUserMessage(message.trim());

        String stopReason = "end_turn";
        long inputTokens = 0, cacheRead = 0, cacheWrite = 0, outputTokens = 0;
        long started = System.currentTimeMillis();

        try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params.build())) {
            Iterator<RawMessageStreamEvent> events = response.stream().iterator();
            while (events.hasNext()) {
                RawMessageStreamEvent event = events.next();
                if (event.isContentBlockDelta()) {
                    // Only visible text is forwarded; thinking deltas (if any) are skipped.
                    event.asContentBlockDelta().delta().text().ifPresent(t -> listener.onText(t.text()));
                } else if (event.isMessageStart()) {
                    Usage usage = event.asMessageStart().message().usage();
                    inputTokens = usage.inputTokens();
                    cacheRead = usage.cacheReadInputTokens().orElse(0L);
                    cacheWrite = usage.cacheCreationInputTokens().orElse(0L);
                } else if (event.isMessageDelta()) {
                    RawMessageDeltaEvent delta = event.asMessageDelta();
                    outputTokens = delta.usage().outputTokens();
                    Optional<StopReason> reason = delta.delta().stopReason();
                    if (reason.isPresent()) {
                        stopReason = stopReasonName(reason.get());
                    }
                }
            }
        }

        log.info("Help assistant reply: user={} model={} stop={} in={} cacheRead={} cacheWrite={} out={} ms={}",
                username, properties.getModel(), stopReason, inputTokens, cacheRead, cacheWrite, outputTokens,
                System.currentTimeMillis() - started);
        return new Result(stopReason, inputTokens, cacheRead, cacheWrite, outputTokens);
    }

    // ------------------------------------------------------------------ helpers

    private record Turn(boolean user, String text) {
    }

    /** Drops junk, truncates long turns, keeps the last N, and ensures the history starts with a user turn. */
    private List<Turn> sanitize(List<AssistantChatRequest.Turn> history) {
        List<Turn> turns = new ArrayList<>();
        if (history != null) {
            for (AssistantChatRequest.Turn t : history) {
                if (t == null || t.getContent() == null || t.getContent().isBlank() || t.getRole() == null) {
                    continue;
                }
                boolean isUser = "user".equalsIgnoreCase(t.getRole());
                boolean isAssistant = "assistant".equalsIgnoreCase(t.getRole());
                if (!isUser && !isAssistant) {
                    continue;
                }
                String text = t.getContent().trim();
                int max = properties.getMaxHistoryTurnChars();
                if (text.length() > max) {
                    text = text.substring(0, max) + " [...]";
                }
                turns.add(new Turn(isUser, text));
            }
        }
        int keep = properties.getMaxHistoryMessages();
        if (turns.size() > keep) {
            turns = new ArrayList<>(turns.subList(turns.size() - keep, turns.size()));
        }
        while (!turns.isEmpty() && !turns.get(0).user) {
            turns.remove(0);
        }
        return turns;
    }

    /**
     * The small per-user block: who they are, what role they hold, and which charity id
     * their scoped URLs use. No contact details beyond first name, nothing about families.
     */
    String buildUserContext(String username) {
        StringBuilder sb = new StringBuilder();
        sb.append("# The signed-in user\n");
        sb.append("- Username: ").append(username).append('\n');

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
                sb.append("- First name: ").append(user.getFirstName().trim()).append('\n');
            }
            TreeSet<String> roles = new TreeSet<>();
            for (Role role : user.getRoles()) {
                String name = role.getName();
                roles.add(ROLE_LABELS.getOrDefault(name, name));
            }
            sb.append("- Role(s): ").append(roles.isEmpty() ? "none assigned" : String.join("; ", roles)).append('\n');
            if (user.getCharity() != null) {
                sb.append("- Charity: ").append(user.getCharity().getCharityName())
                  .append(" (charity id ").append(user.getCharity().getId()).append(")\n");
                sb.append("- Charity-scoped URLs for this user carry that id, e.g. /charity-partner/")
                  .append(user.getCharity().getId()).append("/referrals or /charity-facilitator/")
                  .append(user.getCharity().getId()).append("/dashboard\n");
            }
            if (user.isMustChangePassword()) {
                sb.append("- This account is flagged to change its password at /change-password before using anything else.\n");
            }
        }
        sb.append("- Today's date: ").append(LocalDate.now()).append('\n');
        sb.append("\nAnswer for THIS user's role. Only describe pages and actions their role can reach; "
                + "if they ask about something another role does, say which role handles it.\n");
        return sb.toString();
    }

    /** Effort is an Opus/Sonnet 4.6+ parameter; Haiku 4.5 rejects it, so it is omitted there. */
    static Optional<OutputConfig.Effort> effortFor(String model, String effort) {
        if (effort == null || effort.isBlank() || (model != null && model.toLowerCase().contains("haiku"))) {
            return Optional.empty();
        }
        switch (effort.trim().toLowerCase()) {
            case "low":
                return Optional.of(OutputConfig.Effort.LOW);
            case "medium":
                return Optional.of(OutputConfig.Effort.MEDIUM);
            case "high":
                return Optional.of(OutputConfig.Effort.HIGH);
            default:
                log.warn("Help assistant: unknown app.assistant.effort '{}', using low", effort);
                return Optional.of(OutputConfig.Effort.LOW);
        }
    }

    private static String stopReasonName(StopReason reason) {
        if (StopReason.END_TURN.equals(reason)) {
            return "end_turn";
        }
        if (StopReason.MAX_TOKENS.equals(reason)) {
            return "max_tokens";
        }
        if (StopReason.REFUSAL.equals(reason)) {
            return "refusal";
        }
        return String.valueOf(reason);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return "user";
        }
    }
}
