# Location Partner Feature

**Start date:** 2026-04-24
**Branch:** `feature/location-partner` (branched off `main`)

---

## Branch Strategy (IMPORTANT — remember this later)

- `feature/location-partner` was branched off **`main`** on 2026-04-24.
- `feature/charity-onboarding` is a **separate, parallel in-flight branch** containing:
  - Charity self-service onboarding (registration tokens, team invites)
  - Stripe donation integration (public donate — now disabled)
  - Charity fee payment flow via Stripe Checkout (latest commit: `ef85704`)
- **Merge `feature/charity-onboarding` into `main` first** when ready. Then pull `main` into `feature/location-partner` before merging this branch to main.
- Rationale: keep the two efforts independent so each can be tested and shipped separately.

### Local-only files NOT committed
- `src/main/resources/application-local.properties` contains **Stripe test API secrets** — do not commit. Carries across branches as an uncommitted local modification.
- `.claude/`, `snyk-results.json`, `nul`, `.pptx`, `.jpg` — left untracked.

---

## Design — Option A (Unified with Stay Partner)

After review we determined the initial Phase 1 Location Partner application form (`/location-partner-application/apply`) was ~80% duplication of the existing Stay Partner application. We consolidated to avoid a confusing second form and to keep the codebase lean.

### How it works end-to-end

1. **Single application entry point: `/stay-partner/apply`** (existing). No new apply form; no new landing-page button.
2. Admin reviews applications at `/admin/stay-partner-applications/**` (existing).
3. On the application detail page, admin chooses **one of two approval outcomes**:
   - **"Passive Listing"** (existing behavior) — pick a charity; system creates an inactive `CharityLocation`. No login given.
   - **"Self-Service Location Partner"** (NEW, added in this branch) — system creates a `User`, a `LocationPartner` profile, and a `RegistrationToken` for account creation. Approval email sends a registration link.
4. Applicant receives approval email with a 7-day registration link.
5. Applicant redeems token at `/location-partner/register/{token}` → sets username/password → logs in.
6. Logged-in Location Partner lands on `/location-partner/dashboard` — distinct from `/location-admin/*` (charity-managed) and `/charity-partner/*` (charity team).
7. Partner adds their own `PartnerLocation` records (separate from `CharityLocation`) and marks availability windows on each.
8. Admin gets a zip/date search tool (`/admin/availability-calendar`) that queries across all partners' `AVAILABLE` windows.

### New role
- `LOCATION_PARTNER` — distinct from `LOCATION_ADMIN` (charity-managed) and `CHARITY_PARTNER` (charity team member).

### New tables (from `docs/LOCATION_PARTNER_MIGRATION.sql`)
- `location_partners` — partner profile, linked to `users.id` and optionally `stay_partner_applications.id`
- `partner_locations` — properties owned by a LocationPartner (separate from `charity_locations`)
- `location_availability` — date/time windows per partner location

### Reused (NOT duplicated)
- `stay_partner_applications` table + entity + repository + service submit flow
- `/stay-partner/apply`, `/stay-partner/status`, `/stay-partner/confirmation` public endpoints
- `public/stay-partner-apply.html`, `stay-partner-confirmation.html`, `stay-partner-status.html`
- `registration_tokens` table and `RegistrationTokenService` (new `LOCATION_PARTNER` token type added in Phase 2)
- `StayPartnerApplication.ApplicantType` and `StayPartnerApplication.PropertyType` enums (referenced directly from `LocationPartner` and `PartnerLocation` entities)

### Key design decisions
- **Approval outcome is admin's choice at approval time** — a single application can become either a passive listing OR a self-service partner. Not both.
- **Reuse `RegistrationToken`** — add `LOCATION_PARTNER` as a new `TokenType`. Validate/redeem flow mirrors the charity primary-contact flow.
- **`PartnerLocation` is separate from `CharityLocation`** — owned by the partner, not a charity.
- **Availability windows are affirmative** — partners mark dates free, rather than blocking unavailable dates.
- **FullCalendar.js** for both the partner availability UI and the admin search.

### Open questions still to answer
1. **Tax ID on application:** currently optional on Stay Partner form — keep as-is unless you want to require it.
2. **Admin zip search:** exact match (Phase 5 default) or radius (needs geocoding dep)?
3. **Availability visibility:** Phase 5 is admin-only; defer charity/facilitator visibility.

---

## Revised Phased Plan

### Phase 1 — Foundation (REVISED: minimal)
Complete as of 2026-04-24. After Option A cleanup, Phase 1 now includes:

- `docs/LOCATION_PARTNER_MIGRATION.sql` — seeds `ROLE_LOCATION_PARTNER`, creates `location_partners`, `partner_locations`, `location_availability` tables.
- Entities: `LocationPartner`, `PartnerLocation`, `LocationAvailability` (scaffolded; referenced in Phase 2+).
- Repositories: `LocationPartnerRepository`, `PartnerLocationRepository`, `LocationAvailabilityRepository`.

**Deleted in cleanup (these were duplicates of Stay Partner):**
- `LocationPartnerApplication` entity/repository/service/controller
- `public/location-partner-apply.html`, `location-partner-confirmation.html`, `location-partner-status.html`
- `SecurityConfig` permitAll for `/location-partner-application/**`

**Test for Phase 1:** run the migration, start app, verify 3 new tables exist and role row is seeded, verify app compiles and boots cleanly, verify existing `/stay-partner/apply` still works with zero changes.

### Phase 2 — Approval as Self-Service Partner
- Extend `RegistrationToken` with new `TokenType.LOCATION_PARTNER` (token points to a `LocationPartner` instead of a `Charity`)
- Update `registration_tokens` table if needed (add nullable `location_partner_id` FK; `charity_id` becomes nullable)
- `StayPartnerApplicationService` — add new `approveApplicationAsPartner(id, adminUser, adminNotes)` method
  - Creates `LocationPartner` record (copies contact info from application)
  - Creates `RegistrationToken` of type `LOCATION_PARTNER`
  - Sets `application.status = APPROVED`, records admin/reviewed info
  - Sends approval email with registration link (NEW email template — not the "passive listing" approval email)
- `StayPartnerAdminController` — update approve endpoint/template to let admin pick outcome ("Create passive listing" vs "Invite as Location Partner")
- `RegistrationTokenService` — extend `registerUserFromToken()` to handle the `LOCATION_PARTNER` case (create `User`, attach to `LocationPartner`, assign `LOCATION_PARTNER` role)
- `LocationPartnerRegistrationController` — public endpoints `/location-partner/register/{token}` (GET + POST)
- Template: `public/location-partner-register.html`
- `SecurityConfig` — permit `/location-partner/register/**`
- `CustomAuthenticationSuccessHandler` — redirect LOCATION_PARTNER to `/location-partner/dashboard` (placeholder until Phase 3)

**Test:** submit a Stay Partner application → admin approves as "Location Partner" → applicant gets email → registers → logs in → lands on placeholder dashboard. Also test "passive listing" outcome still works. Token expiry and single-use behavior verified.

### Phase 3 — Location Partner Dashboard & Location CRUD
- `LocationPartnerController` (ROLE_LOCATION_PARTNER): dashboard, `/location-partner/locations` list/new/edit/view
- `LocationPartnerService` — profile access + `PartnerLocation` CRUD scoped to logged-in partner
- Templates: `location-partner/dashboard.html`, `locations.html`, `location-form.html`, `location-view.html`, `fragments/sidebar.html`
- `SecurityConfig` — protect `/location-partner/**` (except `/location-partner/register/**`) with `LOCATION_PARTNER`

**Test:** login → dashboard → add/edit/deactivate locations → scoping enforced (partner cannot see other partners' locations) → no regressions on LOCATION_ADMIN.

### Phase 4 — Availability Windows (Partner Side)
- `LocationAvailabilityService` — add/update/delete windows with overlap validation
- `LocationPartnerController` — availability endpoints per partner location
- Template: `location-partner/availability.html` — FullCalendar click-to-add, drag-to-edit
- Sidebar — "Availability" nav item

**Test:** add/edit/delete windows, overlap handling, calendar renders correctly on multiple locations.

### Phase 5 — Admin Availability Calendar / Zip Search Tool
- `LocationAvailabilityService.searchAvailable(zip, startDate, endDate, minBedrooms, maxGuests)`
- `StayPartnerAdminController` (or new controller) — `/admin/availability-calendar`
- Template: `admin/availability-calendar.html` — filter form + list + FullCalendar
- `home.html` — admin quick link

**Test:** zip search returns matching inventory, date overlap works, only AVAILABLE + active + verified partners shown, clicking a window reveals partner contact.

---

## Progress

- [x] 2026-04-24: Branch created off `main`. Plan saved.
- [x] 2026-04-24: Initial Phase 1 built (with Location Partner application form) — then reviewed and determined to duplicate Stay Partner. User chose Option A (unify).
- [x] 2026-04-24: **Option A cleanup complete.** Deleted duplicate application entity/repo/service/controller/templates. Retained `LocationPartner`, `PartnerLocation`, `LocationAvailability` entities/repos. Migration SQL rewritten to only create the 3 net-new tables + role seed. Entities updated to reference `StayPartnerApplication.ApplicantType` / `PropertyType`.
- [x] 2026-04-24: **Phase 2 complete** — minimal end-to-end flow delivered. Admin approves Stay Partner application as Location Partner → registration email → applicant registers → logs in → dashboard + availability management. Files:
  - `docs/LOCATION_PARTNER_PHASE2_MIGRATION.sql` — relaxes `registration_tokens.charity_id` to nullable, adds `location_partner_id` and `stay_partner_application_id` FKs + indexes.
  - `entity/RegistrationToken.java` — charity nullable; added `locationPartner` + `stayPartnerApplication` FKs; new `TokenType.LOCATION_PARTNER`.
  - `service/RegistrationTokenService.java` — added `createLocationPartnerToken()`; `registerUserFromToken()` now branches on token type (no charity access for LOCATION_PARTNER); new `sendLocationPartnerWelcomeEmail()`.
  - `service/StayPartnerApplicationService.java` — added `approveApplicationAsLocationPartner()` (creates LocationPartner + one PartnerLocation from application data + RegistrationToken) and `buildLocationPartnerApprovalEmailHtml()`.
  - `controller/StayPartnerAdminController.java` — added `POST /admin/stay-partner-applications/{id}/approve-as-partner`.
  - `templates/admin/stay-partner-application-detail.html` — split approve section into "Passive Listing" (existing) + "Invite as Location Partner" (new) side-by-side cards.
  - `controller/LocationPartnerRegistrationController.java` (new) — `GET/POST /location-partner/register/{token}`.
  - `templates/public/location-partner-register.html` (new) — registration form with password match validation.
  - `controller/LocationPartnerController.java` (new, `@PreAuthorize("hasRole('LOCATION_PARTNER')")`) — dashboard + availability CRUD.
  - `service/LocationPartnerService.java` (new) — scopes all lookups to the logged-in partner.
  - `service/LocationAvailabilityService.java` (new) — add/delete windows with overlap validation.
  - `templates/location-partner/dashboard.html`, `availability.html`, `fragments/sidebar.html` (new).
  - `config/SecurityConfig.java` — permit `/location-partner/register/**`; protect `/location-partner/**` with `LOCATION_PARTNER`; add `LOCATION_PARTNER` to `/user/**` allow list.
  - `config/CustomAuthenticationSuccessHandler.java` — `LOCATION_PARTNER` → `/location-partner/dashboard`.
- **Deferred (not yet built, intentional):** Partner editing their property; multi-property; charity/admin booking into partner availability; admin zip-search across partners. Revisit after live testing.
- [x] 2026-04-25: **Phase 3 complete** — Admin can now link a Location Partner's property to one or more charities (many-to-many). Files:
  - `docs/LOCATION_PARTNER_PHASE3_MIGRATION.sql` — creates `partner_location_charities` join table with unique constraint and indexes; ON DELETE CASCADE on both FKs.
  - `entity/PartnerLocationCharity.java` (new) — explicit join entity with audit fields (`createdAt`, `createdBy`).
  - `repository/PartnerLocationCharityRepository.java` (new) — find/exists/count queries.
  - `repository/LocationPartnerRepository.java` — added `findAllByOrderByCreatedAtDesc()`.
  - `service/PartnerLocationAssignmentService.java` (new) — link/unlink with duplicate validation; `getEligibleCharities()` for the add-charity dropdown.
  - `controller/AdminLocationPartnerController.java` (new, ADMIN-only) — list, detail, add link, remove link. Verifies the location belongs to the partner before any mutation.
  - `templates/admin/location-partners.html` (new) — partners list with property summary and charity-link count badges.
  - `templates/admin/location-partner-detail.html` (new) — partner profile, property card, linked charities list with Remove buttons, Add Charity dropdown filtered to non-linked charities.
  - `templates/home.html` — added "Location Partners" admin nav card.
- **Phase 3 deferred:** charities still cannot book against partner properties; partner-side visibility of charity links not surfaced. Both planned for Phase 4.
- [x] 2026-04-25: **Phase 4 complete** — Charities linked to a partner property can now book against the partner's availability windows. Files:
  - `docs/LOCATION_PARTNER_PHASE4_MIGRATION.sql` — adds `bookings.partner_location_id` and `location_availability.booking_id` (both nullable FKs + indexes).
  - `entity/Booking.java` — added nullable `partnerLocation` FK.
  - `entity/LocationAvailability.java` — added nullable `booking` FK (set when a window is BOOKED so we can release it on cancel).
  - `dto/BookingDto.java` — relaxed `@NotNull` on `locationId`, added `partnerLocationId` and `locationSelection` (the form's prefixed value).
  - `repository/PartnerLocationRepository.java` — added `findActiveLinkedToCharity(charityId)` (joins through `partner_location_charities`).
  - `repository/LocationAvailabilityRepository.java` — added `findByBookingId()`.
  - `service/LocationAvailabilityService.java` — added `bookWindow()` (validates window contains booking dates, splits into prefix-AVAILABLE / BOOKED / suffix-AVAILABLE) and `releaseWindowForBooking()` (flips back to AVAILABLE on cancel). `deleteWindow()` now refuses to delete BOOKED windows.
  - `service/BookingService.java` — `createBooking()` parses `locationSelection`, branches between charity and partner flows, requires partner property to be linked to the referral's charity, calls `bookWindow()` after save. `cancelBooking()` calls `releaseWindowForBooking()`.
  - `controller/CharityFacilitatorController.java` and `controller/FacilitatorController.java` — inject `PartnerLocationRepository`, pass `partnerLocations` to the booking form, pre-populate `locationSelection`.
  - `templates/charity-facilitator/booking-form.html` and `templates/facilitator/booking-form.html` — single dropdown with `<optgroup>`s for "Our Locations" + "Partner Properties"; values prefixed `charity:{id}` / `partner:{id}`; bound to `locationSelection`.
  - `templates/location-partner/availability.html` — BOOKED windows shown in amber with status pill, Remove button hidden on BOOKED rows.
- **Out of scope (future):** partner notification email when their property is booked; partner sees list of upcoming bookings on dashboard; merging adjacent AVAILABLE windows when a BOOKED slice is released.
- [x] 2026-04-25: **Phase 5 complete** — Admin partner-bookings calendar at `/admin/partner-bookings-calendar`. Files:
  - `src/main/resources/static/js/vendor/fullcalendar/index.global.min.js` (NEW, ~282 KB) — FullCalendar v6.1.15 self-hosted bundle.
  - `repository/LocationAvailabilityRepository.java` — added `findOverlappingRange(start, end)`.
  - `service/PartnerBookingsCalendarService.java` (NEW) — assembles JSON events; per-partner deterministic colors from a 12-swatch palette; AVAILABLE rendered as `display: 'background'` faint blocks, BOOKED as solid blocks.
  - `controller/AdminPartnerBookingsCalendarController.java` (NEW, ADMIN-only) — page + `/events.json` feed consumed by FullCalendar.
  - `templates/admin/partner-bookings-calendar.html` (NEW) — calendar shell with month/week/list views, partner + charity filters, "Show available windows" toggle (default ON), legend, and click-event modal showing booking + partner details with a "View Partner" link to `/admin/location-partners/{id}`.
  - `templates/home.html` — added "Partner Bookings Calendar" admin card.
  - `templates/admin/documentation.html` — added "Location Partners" sidebar nav + content section, refreshed Stay Partners section to describe the two approval paths, added Location Partner role badge style and overview row/card.
- [x] 2026-04-25: **Phase 6 (multi-property) complete on `feature/location-partner-multi-property` branch.** Partners can now self-add additional properties from their dashboard. Files:
  - `service/LocationPartnerService.java` — added `createPropertyForPartner`, `updatePropertyForPartner`, `setActive`, `getActiveLocationsForPartner`. Sorted properties active-first then alphabetical.
  - `controller/LocationPartnerController.java` — added /properties (list), /properties/new (form), POST /properties (create), /properties/{id}/edit, POST /properties/{id} (update), POST /properties/{id}/deactivate, POST /properties/{id}/activate. Availability page now takes optional `propertyId` query param to scope to a chosen property.
  - `templates/location-partner/properties.html` (new) — table view with status badges and per-row Activate/Deactivate buttons.
  - `templates/location-partner/property-form.html` (new) — shared add/edit form. Address fields are read-only when editing (admin-only change to preserve charity-link geography assumptions).
  - `templates/location-partner/dashboard.html` — rewritten to show ALL properties as cards (instead of a single "Your Property"). Stats show total + active count.
  - `templates/location-partner/availability.html` — added property selector at the top; redirects with `?propertyId=` after add/delete to keep the user scoped.
  - `templates/location-partner/fragments/sidebar.html` — added "Properties" nav item between Dashboard and Availability.
- **Phase 6 design choices:**
  - New properties default to `is_active=true` so partners can immediately mark availability.
  - Phase 3 charity-link gate still protects unauthorized booking — a new property is invisible to charities until admin links it.
  - Address edits go through admin (not self-serve) to avoid invalidating charity-link geography assumptions.
  - Deactivate, not delete (FK preservation for old bookings/availability/charity-links).
  - Existing single-property partners migrate transparently — their existing row is just the first entry in the new list.
- **No DB migration needed** — schema already supported the 1:N relationship (`partner_locations.location_partner_id`).
- [x] 2026-04-25: **Maintenance queue added** — admin can see and act on partner properties that lack any charity link.
  - `repository/PartnerLocationRepository.java` — added `findActiveWithNoCharityLinks()` (returns active properties with zero `partner_location_charities` rows, oldest-first) and `countActiveWithNoCharityLinks()`.
  - `controller/AdminLocationPartnerController.java` — added `GET /admin/location-partners/unlinked` rendering the maintenance page; injects `unlinkedCount` model attribute into the existing partners list view for badge display.
  - `templates/admin/location-partner-unlinked.html` (new) — maintenance queue table with partner, property, location, age, and "Manage Links" action button. Shows green "all caught up" state when count = 0.
  - `templates/admin/location-partners.html` — yellow banner at the top when unlinked properties exist, with a "Review & Fix" button linking to the maintenance page.
  - **No DB changes** — uses NOT EXISTS subquery against existing `partner_location_charities` join table.
