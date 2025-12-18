# Resend Email Integration

This document explains the email configuration for SafelyNested, specifically for deployment on Render.com.

## Why Resend?

Render.com blocks outbound SMTP ports (25, 465, 587) to prevent spam. This means traditional SMTP email (like Gmail SMTP) will timeout on Render.

**Solution:** Use Resend, which sends emails via HTTP API instead of SMTP.

## Setup Instructions

### 1. Create a Resend Account

1. Go to https://resend.com and sign up (free tier: 100 emails/day)
2. Verify your email address

### 2. Get Your API Key

1. Go to https://resend.com/api-keys
2. Click "Create API Key"
3. Give it a name (e.g., "SafelyNested Production")
4. Copy the API key (you won't see it again)

### 3. Configure Render Environment Variables

In your Render dashboard, add these environment variables:

| Variable | Value | Required |
|----------|-------|----------|
| `RESEND_API_KEY` | Your API key from step 2 | Yes |
| `RESEND_FROM_EMAIL` | `SafelyNested <noreply@yourdomain.com>` | No (defaults to test address) |

### 4. (Optional) Add a Custom Domain

For production, you should verify your domain with Resend:

1. Go to https://resend.com/domains
2. Add your domain
3. Add the DNS records they provide
4. Update `RESEND_FROM_EMAIL` to use your domain

## How It Works

The application automatically detects which email backend to use:

- **If `RESEND_API_KEY` is set:** Uses Resend HTTP API
- **Otherwise:** Falls back to SMTP (for local development)

### Files Changed

| File | Purpose |
|------|---------|
| `ResendEmailService.java` | New service that calls Resend HTTP API |
| `EmailService.java` | Updated to use Resend when configured |
| `application-prod.properties` | Resend config with environment variables |

## Local Development

Local development still uses SMTP via `application-local.properties`. No changes needed for local testing.

## Troubleshooting

### Emails not sending on Render
- Check that `RESEND_API_KEY` is set in Render environment variables
- Check Render logs for errors from `ResendEmailService`

### "Resend API key not configured" in logs
- The `RESEND_API_KEY` environment variable is missing or empty

### Emails going to spam
- Verify your domain with Resend
- Use a proper from address (not the test address)

## Free Tier Limits

Resend free tier includes:
- 100 emails/day
- 3,000 emails/month
- 1 custom domain

For higher volumes, see https://resend.com/pricing
