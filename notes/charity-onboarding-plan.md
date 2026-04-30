# Charity Self-Service Onboarding Feature

## Context

Previously, charities could only be added to SafelyNested by system admins manually creating charity records and user accounts. There was no way for a charity to self-register. The landing page "I'm a Charity" button just scrolled to an info section, and the "Partner Login" button went to the login page with no signup option.

This feature adds a complete self-service onboarding flow where charities apply, get reviewed by admins, receive a registration link, and manage their own team — all with minimal admin overhead.

---

## What We Built

### Full Onboarding Flow (Option C — Hybrid)

1. **Public Application** — Charity fills out a form at `/charity-application/apply` with org name, type, EIN, contact info, address, description, mission statement, and estimated referrals per month.
2. **Admin Review** — Admin sees pending applications at `/admin/charity-applications`, views details, and approves or rejects with styled confirmation modals (including processing spinners for email delay).
3. **Approval Creates Charity + Token** — On approval, the system automatically creates a `Charity` entity from the application data and generates a 7-day registration token. An approval email with a "Create Your Account" link is sent to the contact.
4. **Primary Contact Registration** — Contact clicks the link, lands on `/charity-application/register/{token}`, creates their username and password. They become the charity's primary contact. The email domain (e.g. `shelterorg.com`) is extracted and saved as the charity's `allowedEmailDomain`.
5. **Team Management** — Primary contact sees a "Team" item in their sidebar. From `/charity-partner/team` they can invite colleagues. Invites are restricted to the same email domain (e.g. only `@shelterorg.com` addresses). Team members register via their own token links and are auto-linked to the same charity.
6. **Status Lookup** — Applicants can check their application status anytime at `/charity-application/status` using their application number and email.

### Key Security Features

- **Email domain restriction** — Team invites must match the primary contact's email domain
- **CSRF protection** — All POST forms include CSRF tokens
- **Token expiry** — Registration tokens expire after 7 days
- **Single-use tokens** — Tokens are marked as used after registration
- **Duplicate prevention** — Only one pending application per email; existing email addresses blocked from re-registration
- **Primary contact gating** — Only the primary contact can access the team management page

---

## New Files (17)

| # | File | Purpose |
|---|------|---------|
| 1 | `docs/CHARITY_ONBOARDING_MIGRATION.sql` | SQL migration: charity_applications, registration_tokens, team_invites tables + charities alterations |
| 2 | `entity/CharityApplication.java` | JPA entity with ApplicationStatus enum (PENDING, UNDER_REVIEW, APPROVED, REJECTED), app number format "CA-YYYY-NNNN" |
| 3 | `entity/RegistrationToken.java` | Token entity with TokenType enum (PRIMARY_CONTACT, TEAM_MEMBER), expiry, single-use tracking |
| 4 | `entity/TeamInvite.java` | Team invite entity with InviteStatus enum (PENDING, ACCEPTED, EXPIRED, CANCELLED) |
| 5 | `repository/CharityApplicationRepository.java` | Queries: ordered by status, count, duplicate email check, status lookup |
| 6 | `repository/RegistrationTokenRepository.java` | Queries: findByToken, findByEmail, exists check |
| 7 | `repository/TeamInviteRepository.java` | Queries: findByCharity, findByToken, count by status, duplicate check |
| 8 | `service/CharityApplicationService.java` | Submit, approve (creates Charity + RegistrationToken), reject, email notifications |
| 9 | `service/RegistrationTokenService.java` | Validate/redeem tokens, registerUserFromToken (creates user, sets primary contact, extracts email domain, updates team invite) |
| 10 | `service/TeamInviteService.java` | Send invite (with domain validation), list, cancel, invite email |
| 11 | `controller/CharityApplicationController.java` | Public endpoints: apply (GET/POST), confirmation, status (GET/POST), register (GET/POST) |
| 12 | `controller/CharityApplicationAdminController.java` | Admin endpoints: list, detail, approve, reject |
| 13 | `templates/public/charity-application-apply.html` | Multi-section application form |
| 14 | `templates/public/charity-application-confirmation.html` | Success page with app number and "what happens next" steps |
| 15 | `templates/public/charity-application-status.html` | Status lookup form with results display |
| 16 | `templates/public/charity-application-register.html` | Registration form with password strength indicator and match validation |
| 17 | `templates/admin/charity-applications.html` | Admin list with status filter buttons and stats cards |
| 18 | `templates/admin/charity-application-detail.html` | Admin detail view with styled approve/reject modals and processing spinners |
| 19 | `templates/charity-partner/team.html` | Team management: member list, invite form with domain badge, invite list with cancel modal |

## Modified Files (5)

| # | File | Change |
|---|------|--------|
| 1 | `entity/Charity.java` | Added `primaryContact` (User FK) and `allowedEmailDomain` fields |
| 2 | `config/SecurityConfig.java` | Added `/charity-application/**` to permitAll |
| 3 | `controller/CharityPartnerController.java` | Added `isPrimaryContact` model attribute, team management endpoints (GET /team, POST /team/invite, POST /team/invite/{id}/cancel) |
| 4 | `repository/UserRepository.java` | Added `findByCharityId()` |
| 5 | `templates/charity-partner/fragments/sidebar.html` | Added "Team" nav item (visible only to primary contact) |
| 6 | `templates/index.html` | Wired "I'm a Charity" hero button and For Charities section to `/charity-application/apply` |

## Database Tables Added

- `charity_applications` — Stores applications with status, org info, contact, address, admin review fields
- `registration_tokens` — Single-use tokens for account creation (PRIMARY_CONTACT or TEAM_MEMBER type)
- `team_invites` — Team member invitations with status tracking

## Database Columns Added

- `charities.primary_contact_id` — FK to users table, identifies the charity admin
- `charities.allowed_email_domain` — Restricts team invites to matching domain (e.g. `shelterorg.com`)

---

## Email Notifications

| Event | Recipient | Content |
|-------|-----------|---------|
| Application submitted | Applicant | Confirmation with app number, status check link |
| Application approved | Applicant | Congratulations, "Create Your Account" button (7-day token link) |
| Application rejected | Applicant | Rejection with reason, invitation to reapply |
| Account created | New user | Welcome email with username and login link |
| Team invite sent | Invitee | Invitation with personal message, "Create Your Account" button (7-day token link) |

---

## Phase 2 (Planned): Payment/Donation Integration

Still on the `feature/charity-onboarding` branch for future work. Evaluating payment processors (Helcim, Stripe, etc.) for accepting online donations. See main plan file for details on banking requirements and technical approach.
