# Stripe Go-Live Checklist

## Context
Phase 2 (Stripe donation integration) is fully built and tested locally with Stripe test mode. This checklist covers everything needed to switch from test to live payment processing.

---

## Prerequisites (Stripe Account)

- [ ] **Complete Stripe account verification** — Business info, identity verification, EIN/tax ID
  - Stripe Dashboard > Settings > Account details
- [ ] **Add bank account for payouts** — US bank account (routing + account number) where Stripe deposits funds
  - Stripe Dashboard > Settings > Payouts
- [ ] **Business model selection** — "Buyers → SafelyNested → Merchants" (platform/aggregator model)
- [ ] **Product description** — "Online platform facilitating charitable donations for temporary family housing. Donors make one-time contributions to support verified charity organizations funding short-term housing stays for homeless families."
- [ ] **Account fully activated** — Stripe confirms activation (can take 1-2 business days)

---

## Get Live API Keys

1. Go to `dashboard.stripe.com/apikeys`
2. Make sure "Test mode" toggle is **OFF** (top right)
3. Copy the two keys:
   - **Publishable key**: `pk_live_...`
   - **Secret key**: `sk_live_...` (click "Reveal" to see it)

---

## Set Up Live Webhook

1. Go to Stripe Dashboard > **Developers > Webhooks**
2. Click **"Add endpoint"**
3. Endpoint URL: `https://safelynested.onrender.com/api/stripe/webhook` (replace with your actual production URL)
4. Select events to listen for: **`checkout.session.completed`**
5. Click **Save**
6. Copy the **Signing secret**: `whsec_...`

---

## Configure Render Environment Variables

In Render dashboard > your service > **Environment**:

| Variable | Value | Notes |
|---|---|---|
| `STRIPE_SECRET_KEY` | `sk_live_...` | From Stripe live API keys |
| `STRIPE_PUBLISHABLE_KEY` | `pk_live_...` | From Stripe live API keys |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | From live webhook endpoint |

These are read by `application-prod.properties`:
```
stripe.api-key.secret=${STRIPE_SECRET_KEY}
stripe.api-key.publishable=${STRIPE_PUBLISHABLE_KEY}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET}
```

---

## Run Production Database Migration

Run `docs/STRIPE_DONATION_MIGRATION.sql` against the **production** PostgreSQL database. This:
- Makes `donor_id` nullable on the `donations` table
- Adds Stripe tracking columns (stripe_session_id, stripe_payment_intent_id, etc.)
- Adds anonymous donor info columns (donor_email, donor_name)
- Adds payment_source, cover_fees, original_amount columns
- Creates indexes for Stripe lookups

---

## Deploy the Code

1. Commit and push all Phase 2 changes to `feature/charity-onboarding`
2. Merge to `main`
3. Push `main` — Render auto-deploys

---

## Post-Deploy Verification

- [ ] **Check app starts** — Look for "Stripe SDK initialized" in Render logs
- [ ] **Test with a real donation** — Make a $1 donation with a real card
- [ ] **Verify webhook fires** — Check Stripe Dashboard > Developers > Webhooks > recent events
- [ ] **Verify donation record** — Check the `donations` table for the new record with `payment_source = 'STRIPE'`
- [ ] **Verify confirmation email** — If you entered an email, check for the receipt
- [ ] **Refund the test donation** — Stripe Dashboard > Payments > find the payment > Refund

---

## Important Notes

### Local vs Production
- `application-local.properties` uses **test keys** (sk_test_, pk_test_) — local dev always uses test mode
- `application-prod.properties` reads from **environment variables** — production uses live keys
- The Stripe CLI webhook listener (`stripe listen --forward-to ...`) is only for local testing
- In production, Stripe sends webhooks directly to your Render URL

### Security Reminders
- **Never commit live keys** to git — they go in Render environment variables only
- **Never share sk_live_ keys** — they provide full access to your Stripe account
- The `application-local.properties` file contains test keys which are safe but should still be in `.gitignore` for best practice

### Stripe Connect (Future)
The current setup collects all donations into SafelyNested's single Stripe account. If you later want Stripe to automatically split payments and send funds directly to each charity's bank account, you would add Stripe Connect. This is not required to accept donations today — it's a future optimization.

### Fees Summary
| Fee | Rate | Charged By |
|---|---|---|
| Platform fee | 7% | SafelyNested |
| Facilitator fee | 3% | SafelyNested |
| Stripe processing | ~2.9% + $0.30 | Stripe |
| **Total effective fee** | **~12.9% + $0.30** | Combined |

If donors check "cover fees," the 10% platform+facilitator fees are added to their charge so the charity receives 100% of the intended amount. Stripe's processing fee is always deducted by Stripe from the payout.

---

## Rollback Plan
If something goes wrong after going live:
1. In Render, change `STRIPE_SECRET_KEY` to a test key — this immediately stops live payments
2. Or disable the webhook in Stripe Dashboard > Webhooks
3. The `/donate` page will show an error when trying to create sessions with invalid keys, but no charges will be made
