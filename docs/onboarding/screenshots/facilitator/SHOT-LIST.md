# Charity Facilitator Tasks — Screenshot Capture List

**Setup before capturing:**
- Run the app locally (`./mvnw spring-boot:run`).
- Log in as a Charity Facilitator user. Make sure that user's charity has at least:
  - 1 PENDING referral (so the Approve/Reject buttons render)
  - 1 APPROVED referral (so you can create a booking from it)
  - 1 booking in CONFIRMED status (so Check-In button appears)
  - 1 booking in CHECKED_IN status (so Check-Out button appears)
  - 1 verified donation with remaining balance (so the donation funding dropdown shows entries)
- Browser window size: **1440 × 900**.
- File format: **PNG**.
- Save into `docs/onboarding/screenshots/facilitator/` matching the filenames below.

**Redaction reminder:** anywhere a participant's real name, phone, or email appears, blur or replace before saving. Same for any donor names if they're sensitive.

---

| # | Filename | URL / how to get there | What the shot must show | Redact |
|---|---|---|---|---|
| 01 | `01-dashboard.png` | `/charity-facilitator/dashboard` | Full Facilitator Dashboard, both rows of stat cards visible (4 referral cards + 4 booking cards), plus the Recent Referrals and Recent Bookings tables underneath | Participant names |
| 02 | `02-referrals-list.png` | `/charity-facilitator/referrals` | The full referrals list. Show the status filter buttons at the top, and at least 4-5 rows including a mix of statuses (PENDING + APPROVED at minimum) | Participant names |
| 03 | `03-referral-detail-pending.png` | Click into a PENDING referral from the list | The referral detail page in PENDING status. Capture from the top down through the green **Approve Referral** and red **Reject Referral** buttons at the bottom | Participant info |
| 04 | `04-approve-modal.png` | On the same pending referral, click **Approve Referral** | The approve modal open in the foreground, with the Notes textarea visible. Background page slightly visible behind the overlay | Participant info |
| 05 | `05-reject-modal.png` | On a pending referral, click **Reject Referral** | The reject modal with the **required** Reason textarea visible. Type a sample reason like "Out of catchment area" so the textbox isn't empty in the screenshot | — |
| 06 | `06-bookings-list.png` | `/charity-facilitator/bookings` | The full bookings list. Include the status filter and at least 4-5 rows showing CONFIRMED, CHECKED_IN, and one CANCELLED so the variety is clear | Participant names |
| 07 | `07-booking-form-top.png` | From an APPROVED referral detail, click **Create Booking** | Top half of the booking form: the participant info readouts at top, the yellow "Participant's preferred location" callout (if the referral had a selected location), the Location dropdown (open or closed is fine), and the Check-in / Check-out date fields | Participant info |
| 08 | `08-booking-form-donation.png` | Same booking form, scroll down | The "Fund from a Donation" card with a donation **selected** in the dropdown. The auto-filled Payment Status, Payment Method, and Paid By fields should be visible underneath | Donor name if sensitive |
| 09 | `09-checkin-modal.png` | Open a booking that's in CONFIRMED status, click **Check In** | The check-in modal with the optional Notes textarea visible | — |
| 10 | `10-checkout-modal.png` | Open a booking that's in CHECKED_IN status, click **Check Out** | The check-out modal with the optional Notes textarea visible | — |
| 11 | `11-cancel-booking-modal.png` | Open a non-cancelled, non-completed booking, click **Cancel Booking** | The cancellation modal with the **required** reason textarea. Type "Family found alternate housing" or similar so the box isn't empty | — |
| 12 | `12-status-modal.png` | Open any booking, click **Update Status** | The Update Status modal with the **status dropdown open**, showing all options (PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW, COMPLETED) | — |

---

**When you're done:**
- All 12 PNGs in `docs/onboarding/screenshots/facilitator/`
- Tell me and I'll re-render `charity-facilitator-tasks.pdf` with the embedded images.
- Any shot you can't capture or that looks off, just say which numbers and I'll suggest workarounds.
