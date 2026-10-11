package com.learning.learning.service;

import com.learning.learning.config.AssistantProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles the static part of the help assistant's system prompt once at startup:
 * <ol>
 *   <li>the curated guide at {@code classpath:assistant/help-guide.md} (roles, pages,
 *       concepts, how-tos, troubleshooting, and the assistant's rules), and</li>
 *   <li>optionally, the public onboarding guides that Maven packages from
 *       {@code docs/onboarding} to {@code classpath:static/help/*.html}, converted to
 *       plain text.</li>
 * </ol>
 * The result is identical for every user and request, which is what makes it
 * prompt-cacheable. Anything per-user (role, charity) is appended by
 * {@link AssistantService} as a separate, uncached block.
 */
@Service
public class AssistantKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeService.class);

    private static final String CORE_GUIDE = "assistant/help-guide.md";
    private static final String ONBOARDING_PATTERN = "classpath*:static/help/*.html";
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    @Autowired
    private AssistantProperties properties;

    private String staticSystemPrompt = "";
    private int onboardingDocCount = 0;

    @PostConstruct
    public void load() {
        StringBuilder sb = new StringBuilder();
        sb.append(readCoreGuide());

        if (properties.isIncludeOnboardingDocs()) {
            try {
                Resource[] docs = new PathMatchingResourcePatternResolver().getResources(ONBOARDING_PATTERN);
                // Deterministic order keeps the prompt byte-identical across restarts (cache prefix).
                Arrays.sort(docs, Comparator.comparing(r -> String.valueOf(r.getFilename())));
                for (Resource doc : docs) {
                    String name = doc.getFilename();
                    if (name == null || name.equalsIgnoreCase("index.html")) {
                        continue; // the index is just a table of contents
                    }
                    String html = read(doc);
                    String title = extractTitle(html).orElse(name);
                    sb.append("\n\n---\n\n# Guide: ").append(title).append('\n')
                      .append("Read online at /help/").append(name)
                      .append(" (PDF: /help/").append(name.replaceAll("(?i)\\.html$", ".pdf")).append(")\n\n")
                      .append(htmlToText(html));
                    onboardingDocCount++;
                }
            } catch (IOException e) {
                log.warn("Help assistant: could not load onboarding guides from {}: {}", ONBOARDING_PATTERN, e.getMessage());
            }
            if (onboardingDocCount == 0) {
                log.warn("Help assistant: no onboarding guides found on the classpath (expected docs/onboarding/*.html "
                        + "packaged to static/help/ by the Maven resources config). Continuing with the core guide only.");
            }
        }

        staticSystemPrompt = sb.toString();
        log.info("Help assistant knowledge loaded: {} chars (~{} tokens), {} onboarding guide(s)",
                staticSystemPrompt.length(), staticSystemPrompt.length() / 4, onboardingDocCount);
    }

    /** The cacheable, user-independent system prompt. */
    public String getStaticSystemPrompt() {
        return staticSystemPrompt;
    }

    public int getOnboardingDocCount() {
        return onboardingDocCount;
    }

    private String readCoreGuide() {
        try {
            return read(new ClassPathResource(CORE_GUIDE));
        } catch (IOException e) {
            log.error("Help assistant: core guide {} is missing from the classpath. The assistant will answer "
                    + "with no app knowledge until it is restored.", CORE_GUIDE);
            return "You are the help assistant for the SafelyNested shelter referral application. "
                    + "Answer questions about using the application; if you do not know, say so and "
                    + "point the user to the written guides at /help/ or to an administrator.";
        }
    }

    private static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Optional<String> extractTitle(String html) {
        Matcher m = TITLE.matcher(html);
        if (!m.find()) {
            return Optional.empty();
        }
        String t = m.group(1).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? Optional.empty() : Optional.of(t);
    }

    /**
     * Rough HTML to Markdown-ish text: keeps headings, list items and table cells readable,
     * drops scripts, styles, navigation and comments. Good enough for a model to read.
     */
    static String htmlToText(String html) {
        String s = html;
        s = s.replaceAll("(?is)<(script|style|nav|footer)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?is)<!--.*?-->", " ");
        s = s.replaceAll("(?i)<h1[^>]*>", "\n\n# ");
        s = s.replaceAll("(?i)<h2[^>]*>", "\n\n## ");
        s = s.replaceAll("(?i)<h3[^>]*>", "\n\n### ");
        s = s.replaceAll("(?i)<h4[^>]*>", "\n\n#### ");
        s = s.replaceAll("(?i)<li[^>]*>", "\n- ");
        s = s.replaceAll("(?i)</t[dh]>", " | ");
        s = s.replaceAll("(?i)<(br|/p|/div|/tr|/h\\d|/li|/ul|/ol|/table|/section|/article)[^>]*>", "\n");
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
             .replace("&rarr;", "->").replace("&larr;", "<-").replace("&mdash;", "-").replace("&ndash;", "-")
             .replace("&hellip;", "...");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" *\\n *", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}
