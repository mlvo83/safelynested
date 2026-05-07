# Charity Partner Tasks — Screenshot Capture List

**Setup before capturing:**
- Run the app locally (`./mvnw spring-boot:run`).
- Log in as a **Charity Partner** user (NOT a facilitator — for these screens you want the standard Charity Partner view, with the donor sidebar badge etc.). If your only test user is also a facilitator, that's fine; just navigate from the purple sidebar.
- Make sure your test charity has at least:
  - 2-3 referrals in mixed statuses (Pending, Approved)
  - 2-3 invites in mixed statuses (Pending, Opened, Completed)
  - 2-3 active locations
  - At least 1 donor on file (link an existing or have one approved)
  - At least 1 recorded donation
- Browser viewport: **1440 × 900** (Chrome DevTools → Toggle device toolbar → Responsive → 1440×900). Width consistency matters more than the exact number.
- File format: **PNG**.
- Save into `docs/onboarding/screenshots/partner/` matching the filenames below.

**Redaction reminder:** anywhere a real participant name, donor name, phone, email, or address appears, blur or replace before saving. Sensitive document filenames likewise.

---

| # | Filename | URL / how to get there | What the shot must show | Redact |
|---|---|---|---|---|
| 01 | `01-dashboard.png` | `/charity-partner/dashboard` | Full Charity Partner Dashboard, with sidebar visible. Stat cards at top, Quick Actions, and the Recent Referrals table | Participant names |
| 02 | `02-referral-form.png` | Click **+ Create Referral** from the dashboard or referrals list | The full new-referral form (you may need to scroll to fit it; if too long, capture top half showing participant fields and urgency dropdown) | — |
| 03 | `03-referrals-list.png` | `/charity-partner/referrals` | The referrals list with the status filter buttons at top and at least 4-5 rows showing a mix of statuses | Participant names |
| 04 | `04-invite-form.png` | Click **Invites** in sidebar, then **+ Send New Invite** | The Send Invite form: recipient fields, Send Via dropdown, needs description, and message fields | — |
| 05 | `05-invites-list.png` | `/charity-partner/invites` | The Invites list with status filter and at least 3-4 rows including the **Resend** and **Cancel** buttons visible | Recipient names/emails |
| 06 | `06-document-upload.png` | `/charity-partner/documents/upload` | The Document Upload form with the file picker, document type dropdown, referral/invite dropdowns, and description | — |
| 07 | `07-locations-list.png` | `/charity-partner/locations` | The Locations list with at least 2-3 location entries, showing the **Edit / Toggle Active / Delete** buttons | Location names if sensitive |
| 08 | `08-donors-list.png` | `/charity-partner/donors` | The Donors list with the search field and at least 1-2 donors visible. (If you have setup requests too, an alternative is the Setup Requests page at `/donors/setup-requests` — pick whichever is more representative.) | Donor names |
| 09 | `09-record-donation.png` | `/charity-partner/donations/record` | The Record Donation form with the live fee breakdown visible (type a sample $1000 amount so the fee numbers populate) | — |
| 10 | `10-donations-list.png` | `/charity-partner/donations` | The Donations list with at least 1-2 recorded donations, showing amount, fee, source badge, and status | Donor names |
| 11 | `11-profile.png` | `/charity-partner/profile` | The Charity Profile view page showing organization info, contact details, address, and mission. The **Edit Profile** button should be visible | Charity contact info if sensitive (uncommon since this is your own org info) |

---

**When you're done:**
- All 11 PNGs in `docs/onboarding/screenshots/partner/`.
- Tell me and I'll re-render `charity-partner-tasks.pdf` with the embedded images.
- Any shot you can't capture or that looks off, just say which numbers and I'll suggest workarounds.

**Tasks not requiring screenshots:**
- Task 8 (Set up a donor) — points to the existing `charity-donor-onboarding.pdf` and reuses its screenshots.
- Task 13 (Manage team) — points to the existing `charity-facilitator-onboarding.pdf`.
