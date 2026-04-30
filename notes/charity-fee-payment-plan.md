# Charity Fee Payment Flow (Stripe Integration Rework)

## Context

The original Stripe integration had donors paying SafelyNested directly through a public donate page. After reviewing the actual business model, we realized the flow is different: donors give money directly to charities (cash, check, their own payment systems). Charities then record the donation in SafelyNested and pay a 10% platform fee to SafelyNested via Stripe. No fee payment = no record.

The public `/donate` page was disabled (code preserved but routes commented out and landing page links removed). Stripe is now used exclusively for charity partners paying their platform fees.

---

## How It Works

### Step-by-Step Flow

1. **Donor gives money to charity** — This happens outside SafelyNested (cash, check, PayPal, wire, etc.)
2. **Charity partner logs in** to SafelyNested and navigates to **Donations > Record Donation**
3. **Fills in donation details**: donor name, donor email (optional), donation amount, date received, notes
4. **Live fee breakdown** shows what the charity will pay:
   - Donation amount: $1,000
   - Platform fee (7%): $70
   - Facilitator fee (3%): $30
   - **Total fee to pay: $100**
   - Net amount credited to charity: $900
5. **Charity clicks "Pay Fee & Record Donation"** → redirected to Stripe Checkout
6. **Stripe Checkout page** shows the fee amount ($100, not the donation). Charity pays by card or ACH bank transfer
7. **After payment**, Stripe sends a webhook to SafelyNested
8. **Webhook handler** creates the Donation record with:
   - Full donation amount as `grossAmount`
   - Calculated platform and facilitator fees
   - Status: VERIFIED (payment confirmed by Stripe)
   - `paymentSource`: "CHARITY_RECORDED"
   - Fee payment tracked via `feeStripeSessionId`
9. **Charity sees the donation** in their Donations list
10. **Fee receipt email** sent to charity contact

### Accessing the Feature

- **Sidebar**: "Donations" nav item → shows list + "Record Donation" button
- **Dashboard**: "Record Donation" quick action button
- **Direct URL**: `/charity-partner/donations/record`

---

## Donation Status Flow

| Status | Meaning | How it gets here |
|---|---|---|
| **PENDING** | Recorded but not confirmed | Manual admin entry (before verification) |
| **VERIFIED** | Payment confirmed, available for use | Stripe fee payment completed (charity-recorded) OR admin manually verified |
| **ALLOCATED** | Assigned to fund a booking | Facilitator/admin creates a booking funded by this donation |
| **PARTIALLY_USED** | Some funded nights consumed | Booking partially completed (e.g., 3 of 5 nights used) |
| **FULLY_USED** | All funded nights consumed | All booked nights completed |
| **CANCELLED** | Donation cancelled or rejected | Admin cancelled or verification rejected |

### Typical Flow
```
Charity pays fee → VERIFIED → Facilitator books housing → ALLOCATED → Guest stays → PARTIALLY_USED → All nights used → FULLY_USED
```

### Verification Status (separate from donation status)
| Verification | Meaning |
|---|---|
| **PENDING** | Awaiting admin review (manual entries only) |
| **VERIFIED** | Confirmed as legitimate |
| **REJECTED** | Admin rejected the donation |

For charity-recorded donations (via Stripe fee payment), both `status` and `verificationStatus` are set to VERIFIED immediately because the Stripe payment serves as proof.

---

## Payment Sources

Each donation has a `paymentSource` field that tracks how it was recorded:

| Source | Meaning | Who creates it |
|---|---|---|
| **MANUAL** | Admin entered it manually in the admin panel | System admin |
| **CHARITY_RECORDED** | Charity partner recorded it and paid the 10% fee via Stripe | Charity partner |
| **STRIPE** | Online donation through public donate page (currently disabled) | Anonymous donor |

---

## Fee Structure

| Fee | Rate | Paid by | Goes to |
|---|---|---|---|
| Platform fee | 7% of donation | Charity | SafelyNested (platform maintenance) |
| Facilitator fee | 3% of donation | Charity | SafelyNested (facilitator services) |
| **Total** | **10%** | **Charity** | **SafelyNested** |

### Example: $1,000 donation
- Donor gives $1,000 directly to the charity
- Charity records it in SafelyNested
- Charity pays $100 fee (10%) to SafelyNested via Stripe
- Stripe charges the charity ~$3.20 processing fee (2.9% + $0.30 on the $100 fee)
- Donation recorded: grossAmount=$1,000, netAmount=$900, platformFee=$70, facilitatorFee=$30
- Net amount available for booking housing: $900

### Minimum donation
- $5.00 (so the 10% fee is at least $0.50, which is Stripe's minimum charge)

---

## Payment Methods (Stripe Checkout)

Charities can pay the fee using:
- **Credit/Debit Card** — instant processing
- **ACH Bank Transfer (US Bank Account)** — lower fees (0.8% capped at $5 vs 2.9% + $0.30 for cards)

For ACH, Stripe uses Plaid for instant bank verification in live mode. In test mode, use:
- Routing: `110000000`, Account: `000123456789`
- Or select "Test Institution" in the bank search

---

## Nights Funded Calculation

When a donation is recorded, the system calculates how many nights of housing it can fund:

```
nightsFunded = floor(netAmount / averageNightlyRate)
```

- `netAmount` = grossAmount - platformFee - facilitatorFee (= 90% of donation)
- `averageNightlyRate` = average of all active nightly rates for the charity's locations
- If no nightly rates are configured for the charity, nightsFunded = 0

---

## Webhook Architecture

### How Stripe communicates with SafelyNested

1. **Stripe CLI (local development)**: `stripe listen --forward-to localhost:8080/api/stripe/webhook`
2. **Production**: Stripe sends events directly to `https://yourdomain.com/api/stripe/webhook`

### Events handled
- `checkout.session.completed` — card payments (instant)
- `checkout.session.async_payment_succeeded` — ACH payments (delayed, typically a few seconds in test, 1-4 business days in production)

### Webhook routing
The webhook handler checks `session_type` in Stripe metadata:
- `session_type = "FEE_PAYMENT"` → `handleFeePaymentCompleted()` (creates charity-recorded donation)
- No session_type → `handlePublicDonationCompleted()` (creates online donation — currently disabled)

### Idempotency
- Fee payments: checked via `feeStripeSessionId` (unique index prevents duplicates)
- Public donations: checked via `stripeSessionId` (unique index prevents duplicates)
- If the webhook delivers the same event twice, the second attempt is logged and skipped

### Session deserialization
The webhook handler retrieves the full session from Stripe's API using the session ID from the raw event JSON. This is more reliable than deserializing from the event payload, which can fail with certain payment method types (e.g., ACH).

---

## Database Fields Added for This Feature

On `donations` table:

| Column | Type | Purpose |
|---|---|---|
| `date_received` | DATE | When the charity actually received the donation from the donor |
| `fee_stripe_session_id` | VARCHAR(255) | Stripe Checkout session ID for the fee payment |
| `fee_stripe_payment_intent_id` | VARCHAR(255) | Stripe PaymentIntent ID for the fee payment |

Previously added (public donation phase):

| Column | Type | Purpose |
|---|---|---|
| `stripe_session_id` | VARCHAR(255) | For public online donations (currently disabled) |
| `stripe_payment_intent_id` | VARCHAR(255) | For public online donations (currently disabled) |
| `donor_email` | VARCHAR(255) | Donor's email (for anonymous/charity-recorded donations without a Donor entity) |
| `donor_name` | VARCHAR(255) | Donor's name |
| `payment_source` | VARCHAR(50) | "MANUAL", "CHARITY_RECORDED", or "STRIPE" |
| `cover_fees` | BOOLEAN | Whether donor opted to cover fees (public donations) |
| `original_amount` | DECIMAL(10,2) | Original amount before fee adjustment |

---

## Files Created

| File | Purpose |
|---|---|
| `docs/CHARITY_FEE_PAYMENT_MIGRATION.sql` | SQL migration for date_received and fee Stripe fields |
| `templates/charity-partner/record-donation.html` | Recording form with live fee calculator |
| `templates/charity-partner/record-donation-success.html` | Success page after fee payment |
| `templates/charity-partner/record-donation-cancel.html` | Cancel page |
| `templates/charity-partner/donations.html` | Donations list with source badges |

## Files Modified

| File | Change |
|---|---|
| `entity/Donation.java` | Added dateReceived, feeStripeSessionId, feeStripePaymentIntentId |
| `repository/DonationRepository.java` | Added findByFeeStripeSessionId |
| `service/StripeService.java` | Added createFeePaymentSession, refactored webhook routing (public vs fee payment), fixed session deserialization fallback, added fee receipt email |
| `service/DonationService.java` | Added findByFeeStripeSessionId pass-through |
| `controller/CharityPartnerController.java` | Added StripeService dep, 6 new endpoints (record form, create-fee-session, success, cancel, calculate-fees, donations list) |
| `config/SecurityConfig.java` | Disabled /donate/** permitAll (commented out) |
| `templates/charity-partner/fragments/sidebar.html` | Added "Donations" nav item |
| `templates/charity-partner/dashboard.html` | Added "Record Donation" quick action button |
| `templates/index.html` | Removed public donate links from nav, hero, and CTA sections |

---

## Public Donate Page (Disabled)

The public `/donate` page code is preserved but disabled:
- `SecurityConfig.java`: `/donate/**` permitAll is commented out (route now requires authentication)
- `index.html`: All donate links removed from landing page
- `DonateController.java`, `donate.html`, `donate-success.html`, `donate-cancel.html`: Files still exist, untouched
- To re-enable: uncomment the SecurityConfig line and add landing page links back

---

## Local Testing Setup

1. Start the Stripe CLI: `/c/Users/JAM/bin/stripe listen --forward-to localhost:8080/api/stripe/webhook`
2. Copy the `whsec_...` secret if it changed and update `application-local.properties`
3. Start the app
4. Log in as a charity partner
5. Go to Donations > Record Donation
6. Fill in details, pay the fee with test card `4242 4242 4242 4242`
7. Or test ACH: select US Bank Account, use Test Institution, routing `110000000`, account `000123456789`
8. Verify donation appears in the donations list and database

## Production Setup

See `notes/stripe-go-live-checklist.md` for full deployment instructions including Render environment variables, live webhook setup, and verification steps.
