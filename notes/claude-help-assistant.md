# In-app Claude help assistant

Built 2026-10-04 on branch `feature/claude-help-assistant`. Phase 1: a read-only
"how do I use the app" chat for signed-in users. It explains pages, gives step-by-step
instructions, and points to the written guides. It cannot take actions or read records.

## What the user sees

A round **Help** button in the bottom-right corner of every signed-in page. Clicking it opens
a small chat panel with a welcome message and three starter questions. Answers stream in
word by word. In-app paths in an answer (for example `/charity-partner/referrals`) are
clickable. The conversation is kept for the browser tab (sessionStorage) and survives page
navigation; **Clear** starts over. The panel is full-screen on phones.

When the assistant is not configured (no API key) the button does not render at all.

## How it works

```
browser widget ──POST /assistant/chat (JSON: message + history, CSRF header)──▶ AssistantController
                                                                                     │ validates, rate-limits,
                                                                                     │ hands off to assistantExecutor
                                                                                     ▼
                                                                              AssistantService
                                                                                     │ builds prompt, streams
                                                                                     ▼
                                                                         Anthropic Messages API (SSE)
                        ◀── Server-Sent Events: delta {text} … done {stopReason} | error {message} ──┘
```

Prompt layout, in order (order matters for prompt caching):

1. **Static knowledge block** (cached for one hour with `cache_control`): the curated guide
   `src/main/resources/assistant/help-guide.md` followed by the plain-text version of the
   public onboarding guides. Identical for every request, so repeat questions read it from
   cache at a fraction of the input price.
2. **Per-user block** (not cached): username, first name, role labels with their home pages,
   charity name and id (so scoped URLs come out right), the must-change-password flag, and
   today's date.
3. The last 20 turns of the chat, then the new question.

The onboarding guides come from `docs/onboarding/*.html`, which the Maven resources config
already packages to `classpath:static/help/` for the public `/help/` site. The assistant
loads them from there at startup, so there is one source of truth. If they are missing
(for example a non-Maven build), the assistant logs a warning and continues with the
curated guide only.

## Files

| File | Role |
|---|---|
| `pom.xml` | dependency `com.anthropic:anthropic-java:2.68.0` |
| `application.properties` | `app.assistant.*` settings (see below) |
| `config/AssistantProperties.java` | typed settings, `isEnabled()` |
| `config/AssistantConfig.java` | `assistantExecutor` thread pool (4 to 8 threads, queue 50) |
| `service/AssistantKnowledgeService.java` | assembles the static system prompt at startup |
| `service/AssistantService.java` | Anthropic client, prompt building, streaming, usage logging |
| `service/AssistantRateLimiter.java` | per-user minute and day caps |
| `controller/AssistantController.java` | `POST /assistant/chat` (SSE), `GET /assistant/status` |
| `dto/AssistantChatRequest.java` | request body |
| `resources/assistant/help-guide.md` | the curated knowledge base and the assistant's rules |
| `templates/fragments/assistant-widget.html` | button + panel + styles, included on every signed-in page |
| `static/js/assistant.js` | widget behaviour, SSE parsing, markdown-lite rendering |
| `config/SecurityConfig.java` | `/assistant/**` requires login |

Every signed-in template ends with
`<div th:replace="~{fragments/assistant-widget :: widget}"></div>` before `</body>`.
The fragment itself checks `isAuthenticated()` and `@assistantService.isEnabled()`, so the
include is harmless anywhere. Remember to add it to new authenticated templates.

## Configuration

All in `application.properties`, overridable by environment variables:

| Property | Env var | Default | Notes |
|---|---|---|---|
| `app.assistant.api-key` | `ANTHROPIC_API_KEY` | blank (off) | the one server-side key |
| `app.assistant.model` | `ASSISTANT_MODEL` | `claude-opus-5-5` | or `claude-sonnet-5-5`, `claude-haiku-4-5` |
| `app.assistant.effort` | `ASSISTANT_EFFORT` | `low` | low / medium / high; skipped for Haiku |
| `app.assistant.max-output-tokens` | | 2048 | thinking + answer |
| `app.assistant.max-message-chars` | | 2000 | |
| `app.assistant.max-history-messages` | | 20 | |
| `app.assistant.per-minute-limit` | | 10 | per user, per instance |
| `app.assistant.per-day-limit` | | 150 | per user, per instance |
| `app.assistant.timeout-seconds` | | 90 | |
| `app.assistant.include-onboarding-docs` | | true | |

## Setting up the SafelyNested Anthropic account (to do)

1. Go to https://console.anthropic.com and create an account using a SafelyNested email
   address (not a personal one), so billing and ownership stay with the organisation.
2. In **Settings → Billing**, add a payment method and buy initial credits. Set a monthly
   spend limit there as a safety net.
3. In **Settings → API keys**, create a key named something like `safelynested-prod`.
   Copy it once; it is not shown again.
4. In Render, open the SafelyNested service → **Environment** and add
   `ANTHROPIC_API_KEY` = the key. Optionally add `ASSISTANT_MODEL` if you do not want the
   Opus default. Save; Render redeploys.
5. Watch the deploy log for `Help assistant is ON: model=...` and
   `Help assistant knowledge loaded: ... onboarding guide(s)`. Then sign in and the
   **Help** button should appear.
6. Optional: a second key `safelynested-local` for development. Put it in the IntelliJ run
   configuration's environment variables, never in `application-local.properties` (that file
   is tracked by git).

Rotate the key from the Console if it is ever exposed; the app only needs a restart with the
new value.

## Cost

Rough per-question cost with the knowledge block cached (about 20 to 25 thousand tokens):

| Model | Cached read of knowledge | Typical answer | About per question |
|---|---|---|---|
| Opus 5.5 | ~$0.005 | ~$0.01 | 1 to 2 cents |
| Sonnet 5.5 | ~$0.003 | ~$0.004 | under 1 cent |
| Haiku 4.5 | ~$0.002 | ~$0.002 | under half a cent |

Writing the cache costs about one full-price read of the block once per hour of activity.
The per-user daily cap of 150 questions bounds the worst case per account. Usage per reply is
logged at INFO as `Help assistant reply: user=... in=... cacheRead=... out=...`; if
`cacheRead` stays at zero, something is changing the static block between requests.

## Safety and privacy

- One key, server-side only. Users never see it and cannot call the API directly.
- No tools: the model cannot create, change, approve, email or delete anything.
- Nothing from the database except the user's own first name, role, charity name and id is
  placed in a prompt. No referral, family, donor or payment data.
- The guide instructs the model not to request or echo personal details about families, and
  the widget footer says the same to the user.
- A `refusal` stop reason is turned into a polite "I can't help with that one"; a
  `max_tokens` stop appends a "cut short" note.
- Logs record token counts and the username, never message text.

## Local testing

1. Set `ANTHROPIC_API_KEY` in the run configuration and start the app on port 8585.
2. Sign in as any training account (`docs/TRAINING_ACCOUNTS.md`) and open the Help button.
3. Try a role question ("how do I approve a referral?") and a wrong-role question; check the
   answer only mentions pages that role can open.
4. Try a non-app question; it should decline politely.
5. Hit `GET /assistant/status` to confirm `{"enabled":true}`.
6. Remove the env var and restart: the button must disappear.

## Updating the knowledge

Edit `src/main/resources/assistant/help-guide.md` when pages, menus or flows change; it is the
part of the prompt most likely to drift from the app. Keep it factual (the model is told not
to invent pages) and avoid timestamps or anything non-deterministic, since the whole block is
the cache prefix. The onboarding guides update themselves whenever `docs/onboarding` changes.

## Phase 2 ideas (not built)

- Tool use so the assistant can act (create a draft referral, look up a booking) behind
  explicit confirmation and per-role permission checks.
- Persist questions and token usage to a table for an admin usage report.
- Pass the current page path to the assistant so answers start from where the user is.
- Server-side refusal fallbacks (`fallbacks: "default"`) once a beta-API builder is wired in.
