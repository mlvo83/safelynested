# Stripe Donation Integration (Phase 2)

## Context

SafelyNested had a complete manual donation tracking system where admins recorded donations by hand. Phase 2 adds public online donations via Stripe Checkout so anyone can donate to a charity without creating an account.

---

## What We Built

### Public Donation Flow
1. Donor visits `/donate` (central page with charity selector) or `/donate/{charityId}` (pre-selected charity)
2. Selects a charity, enters amount, sees live fee breakdown (7% platform + 3% facilitator)
3. Can optionally check "Cover the fees" so 100% of their intended amount goes to the charity
4. Enters name and email (optional — for receipt)
5. Clicks "Donate" → redirected to Stripe's hosted checkout page (PCI-compliant, card info never touches our server)
6. Completes payment with card → redirected to `/donate/success`
7. Stripe sends webhook to `/api/stripe/webhook` → creates a verified Donation record in the database
8. Donor receives a confirmation email with donation details and nights funded

### Key Features
- **No account required** — fully anonymous public donations
- **Live fee calculator** — AJAX-powered fee breakdown updates as donor types
- **Cover fees option** — adjusts gross amount so charity receives 100% of intended donation (formula: `adjustedGross = amount / 0.90`)
- **Preset amount buttons** — $25, $50, $100, $250 quick-select
- **Idempotent webhook** — unique index on stripe_session_id prevents duplicate donations
- **Graceful success page** — handles race condition where webhook may not have fired yet when user lands on success page
- **Loading overlay** — "Redirecting to secure payment..." spinner during Stripe redirect

---

## New Files (9)

| # | File | Purpose |
|---|------|---------|
| 1 | `docs/STRIPE_DONATION_MIGRATION.sql` | DB migration: nullable donor_id, Stripe tracking fields, payment_source, cover_fees |
| 2 | `config/StripeConfig.java` | Initializes Stripe SDK with API key on startup |
| 3 | `service/StripeService.java` | Creates Checkout Sessions, handles webhooks, creates Donation records, sends confirmation emails |
| 4 | `controller/DonateController.java` | Public routes: /donate, /donate/{charityId}, /donate/create-session, /donate/success, /donate/cancel, /donate/calculate-fees |
| 5 | `controller/StripeWebhookController.java` | POST /api/stripe/webhook — receives and verifies Stripe events |
| 6 | `templates/public/donate.html` | Donation form with charity selector, amount presets, live fee breakdown, cover-fees checkbox |
| 7 | `templates/public/donate-success.html` | Thank you page with donation details and nights funded |
| 8 | `templates/public/donate-cancel.html` | Cancellation page with reassurance message |
| 9 | `notes/stripe-go-live-checklist.md` | Full checklist for switching from test to live mode |

## Modified Files (7)

| # | File | Change |
|---|------|--------|
| 1 | `pom.xml` | Added `com.stripe:stripe-java:28.2.0` dependency |
| 2 | `application-prod.properties` | Added Stripe env var references (STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, STRIPE_WEBHOOK_SECRET) |
| 3 | `entity/Donation.java` | Made donor nullable, added stripeSessionId, stripePaymentIntentId, donorEmail, donorName, paymentSource, coverFees, originalAmount fields + getDonorDisplayName() helper |
| 4 | `repository/DonationRepository.java` | Changed JOIN FETCH → LEFT JOIN FETCH (3 queries), added findByStripeSessionId, findByStripePaymentIntentId |
| 5 | `config/SecurityConfig.java` | Added /donate/** and /api/stripe/** to permitAll, CSRF exception for webhook |
| 6 | `service/BookingService.java` | Null-safe donor display name (2 locations) |
| 7 | `controller/AdminController.java` | Null-safe donor display name in search filter |
| 8 | `templates/index.html` | Added "Donate" nav link, "Donate Now" buttons in hero and CTA sections |

## Database Changes

Columns added to `donations` table:
- `stripe_session_id` VARCHAR(255) — unique partial index
- `stripe_payment_intent_id` VARCHAR(255) — unique partial index
- `donor_email` VARCHAR(255) — for anonymous donors
- `donor_name` VARCHAR(255) — for anonymous donors
- `payment_source` VARCHAR(50) DEFAULT 'MANUAL' — 'MANUAL' or 'STRIPE'
- `cover_fees` BOOLEAN DEFAULT FALSE
- `original_amount` DECIMAL(10,2) — donor's intended amount before fee coverage

Column modified:
- `donor_id` — changed from NOT NULL to nullable (for anonymous online donations)

---

## Security Notes

### API Keys — CRITICAL
- **Test keys** (`sk_test_...`, `pk_test_...`) are in `application-local.properties` for local development ONLY
- **`application-local.properties` must NEVER be committed to git** — it contains test secrets
- **Live keys** (`sk_live_...`, `pk_live_...`) go in **Render environment variables**, NOT in any file
- `application-prod.properties` reads from env vars: `${STRIPE_SECRET_KEY}`, `${STRIPE_PUBLISHABLE_KEY}`, `${STRIPE_WEBHOOK_SECRET}`
- **Never share or commit `sk_live_` keys** — they provide full access to your Stripe account

### Environment Variable Setup (Render)
When going live, set these in Render dashboard > your service > Environment:

| Variable | Value |
|---|---|
| `STRIPE_SECRET_KEY` | `sk_live_...` (from Stripe Dashboard, test mode OFF) |
| `STRIPE_PUBLISHABLE_KEY` | `pk_live_...` (from Stripe Dashboard, test mode OFF) |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` (from Stripe Dashboard > Webhooks > your live endpoint) |

### Webhook Security
- Webhook endpoint (`/api/stripe/webhook`) is public but verifies Stripe's signature header
- CSRF is disabled only for the webhook endpoint — all other POST forms still use CSRF tokens
- Invalid signatures return 400 Bad Request

### Local Testing
- Uses Stripe test mode keys (no real charges)
- Stripe CLI forwards webhooks: `stripe listen --forward-to localhost:8080/api/stripe/webhook`
- Test card: 4242 4242 4242 4242 (any future expiry, any CVC)

---

## Fee Structure

| Fee | Rate | Who Pays |
|---|---|---|
| Platform fee | 7% of gross | Deducted from donation |
| Facilitator fee | 3% of gross | Deducted from donation |
| Stripe processing | ~2.9% + $0.30 | Deducted by Stripe from payout |
| **Total effective** | **~12.9% + $0.30** | Combined |

When "Cover fees" is checked: donor pays `amount / 0.90` so the charity receives the full intended amount after platform + facilitator fees. Stripe's processing fee is separate and always deducted from payouts.

---

## Status
- Code: Complete and tested with Stripe test mode
- Branch: `feature/charity-onboarding`
- Go-live: Waiting on Stripe account verification (see `notes/stripe-go-live-checklist.md`)
- Not yet merged to main
