# Multi-Charity Facilitator Feature Plan

**Branch:** `feature/multi-charity-facilitator`
**Started:** 2026-05-07

---

## Goal

Allow a single user to facilitate for multiple charities — they log in once, land on a hub of charity cards, click into one charity at a time, and use the full facilitator experience scoped to that charity.

## Decisions

- **New role:** `ROLE_MULTI_FACILITATOR`
- **URL-scoped, not session-scoped:** charity ID lives in the URL (`/charity-facilitator/{charityId}/...`). Tab-safe, bookmarkable, audit-friendly.
- **Unified URL space:** existing single-charity `CHARITY_FACILITATOR` users also move to the new URL pattern, with redirects from legacy URLs.
- **Audit-trail entity:** dedicated `MultiFacilitatorCharity` entity records `createdAt` and `createdBy` for every charity assignment.
- **Powers when picked into a charity:** full facilitator experience — referrals, bookings, check-in/out, cancellations, donation funding, all of it.
- **Charity assignment:** done by SafelyNested admin via the existing `/admin/users` form (extended with a multi-charity selector).

## Phases (test after each)

| # | Phase | What |
|---|---|---|
| 1 | Foundation | New role, new table `multi_facilitator_charities`, entity, repo, service helpers (`canFacilitateForCharity`, `getAuthorizedCharities`). |
| 2 | URL refactor | Add `{charityId}` to every facilitator route; per-request authorization; `SecurityConfig` matchers updated; legacy URL redirect for backward compat. |
| 3 | Multi-facilitator hub | `MultiFacilitatorController`, `hub.html` with charity cards (pending-referrals badge), login routing for the new role. |
| 4 | Admin tooling | Extend `/admin/users` to multi-select charities for users with `ROLE_MULTI_FACILITATOR`. |
| 5 | UX polish | "Operating as: *Charity*" banner across the top of facilitator pages with "back to all charities" link for multi-facilitators. |
| 6 | Doc updates | Update `charity-facilitator-tasks.html`/PDF to reflect the new URLs; add a section describing the multi-facilitator hub. |

## Schema (Phase 1)

```sql
INSERT INTO roles (name)
SELECT 'ROLE_MULTI_FACILITATOR'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_MULTI_FACILITATOR');

CREATE TABLE multi_facilitator_charities (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    charity_id          BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    CONSTRAINT uq_multi_facilitator_user_charity UNIQUE (user_id, charity_id)
);

CREATE INDEX idx_mfc_user ON multi_facilitator_charities(user_id);
CREATE INDEX idx_mfc_charity ON multi_facilitator_charities(charity_id);
```

## Authorization model

- `canFacilitateForCharity(username, charityId)` returns `true` if either:
  - User has `ROLE_CHARITY_FACILITATOR` and `user.charity_id == charityId`, **or**
  - User has `ROLE_MULTI_FACILITATOR` and there's a row in `multi_facilitator_charities` for `(user_id, charity_id)`.
- Every method in `CharityFacilitatorController` calls this at the top with the URL's `{charityId}` and rejects with a 403/redirect if not authorized.

## URL examples (new pattern)

| Old | New |
|---|---|
| `/charity-facilitator/dashboard` | `/charity-facilitator/{charityId}/dashboard` |
| `/charity-facilitator/referrals` | `/charity-facilitator/{charityId}/referrals` |
| `/charity-facilitator/referrals/{id}/approve` | `/charity-facilitator/{charityId}/referrals/{id}/approve` |
| `/charity-facilitator/bookings/new?referralId=X` | `/charity-facilitator/{charityId}/bookings/new?referralId=X` |

## Login routing

- `ROLE_CHARITY_FACILITATOR` (existing single-charity) → redirected to `/charity-facilitator/{their_charity_id}/dashboard`.
- `ROLE_MULTI_FACILITATOR` (new) → redirected to `/multi-facilitator/hub`.

---

## Status: Phase 5b sub-phases — what got shipped

| # | Phase | Status |
|---|---|---|
| 5b-0 | Foundation (SecurityConfig matcher, `resolvePartnerCharity` helper, plan doc) | ✓ shipped |
| 5b-1 | Referrals charity-scoped paths (list/view/new/edit/cancel) | ✓ shipped |
| 5b-2 | Invites charity-scoped paths (list/send/resend/cancel) + service overloads | ✓ shipped |
| 5b-3 | Locations charity-scoped paths (list/view/new/edit/toggle/delete) | ✓ shipped |
| 5b-4 | Documents charity-scoped paths (list/upload/download/delete) + service refactor | ✓ shipped |
| 5b-5 | Donors + Donations + Stripe charity-scoped paths (with charity-scoped Stripe success/cancel URLs) | ✓ shipped |
| 5b-6 | Profile + Team charity-scoped paths | ✓ shipped |
| 5b-7 | Legacy URL redirects | **deferred** — app not yet in production; legacy URLs still serve single-charity users; multi-facs navigate via sidebar (scoped URLs only). Revisit if/when external bookmarks become a concern. |

## Notable bug fixes shipped alongside 5b

These were latent bugs surfaced (or introduced and immediately fixed) during the 5b refactor:

1. **CSRF token missing from document-upload form** — `multipart/form-data` posts need the token as a hidden input; the meta-tag/header pattern doesn't apply. Pre-existing.
2. **Default upload limit too small** — Spring Boot defaults to 1MB but the form claims 10MB. Bumped `spring.servlet.multipart.max-file-size` and `max-request-size` to 10MB.
3. **`sec:authorize="hasRole('CHARITY_PARTNER')"` gates everywhere** — hid creation buttons from facilitators. Replaced with `hasAnyRole('CHARITY_PARTNER','CHARITY_FACILITATOR','MULTI_FACILITATOR')` across the partner side.
4. **Donor search missed business donors** — `searchDonorsByCharity` only queried `user.firstName/lastName/email`. Added `businessName` and `contactName` to the search clause.
5. **No "Set Up a Donor" button on the Donors list** — added it to `donors.html` header.
6. **Profile-edit form binding silently failed for multi-facilitators** — `@ModelAttribute("charity")` returned null for them, which collided with the implicit `@ModelAttribute Charity updatedCharity` parameter name. Renamed parameter to `@ModelAttribute("updatedCharity")` to avoid the model-attribute key collision.
