# Flow 10 — Entrant reservations frontend

> AI use: GPT-6 (OpenAI), through Codex, assisted me with Flow 10.

Last updated: November 3, 2026.

Scope: [Flow 10 #127](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/127),
using the existing reservation backend. Raghav authored stories #53–#55; this
branch implements the entrant list, details and cancellation presentation for
those stories. His issue assignments were not changed.

## Acceptance and implementation

| Story | Frontend behaviour | Verification |
| --- | --- | --- |
| #55 | The entrant's Active tab shows current and future confirmed reservations, soonest first. Cards show campsite, dates, nights, guests and status; an empty tab offers Browse campsites. | `EntrantReservationsControllerTest` covers entrant scoping, sorting and live updates. `EntrantReservationsScreenTest` covers cards, tabs, loading and browse. |
| #54 | The Past tab retains completed, cancelled and rejected reservations. Details use the same status label as cards. | Controller tests cover all terminal outcomes and changes at checkout. Compose tests cover Past statuses and completed details. |
| #53 | A confirmed reservation can be cancelled before check-in, after a confirmation dialog. Keep closes the dialog. Cancellation moves the stay to Past and shows a result; refusal remains visible. | Controller tests cover confirmation, dismissal, successful cancellation, freed nights and refusal. The Compose test covers confirmation and Keep. The repository's transaction and rules tests cover backend ownership and calendar changes. |

## Figma alignment

The [team's Flow 10 Figma page](https://www.figma.com/design/usfTYZWNV25DRKPhZJjPdE/Lambda-Campsite-UI?node-id=59356-2640)
was inspected read-only. The list follows Active `59367:2700`, Past `59367:2848`,
and empty `59367:2982`; details, confirmation and cancelled states follow
`59370:3789`, `59370:3951` and `59370:4141`. The screens use the shared
forest/mint theme, 28dp rounded content surface, 40dp segmented control,
16dp reservation cards, shared bottom navigation, and the exact Figma SVG
icons rendered as transparent 3× PNGs. The entrant Home, Reservations and Profile
tab icons also use exact Figma assets. Identical tent, chevron and check assets
already committed for Flow 11 are reused. The photo block follows Figma's
placeholder because the current image repository has no read API. The mockup's
"Get cancellation confirmation" action is outside #53–#55 and has no existing
confirmation-export implementation, so this flow shows the cancellation result
and timestamp without a dead button.

## Code structure

`LambdaCampsiteApp` routes the entrant Reservations tab and pushed details
screen. Both screens share a parent `EntrantReservationsController` and its
live `ReservationRepository.myReservations()` feed. Compose does not call
Firestore or implement booking rules. The repository's existing `cancel()`
transaction checks ownership and dates and releases the booked nights.
The details screen separately requests the campsite location when available;
the reservation record itself supplies the reliable campsite name and dates.

## Local verification

- `compileDebugKotlin` and targeted Flow 10 controller unit tests passed.
- All three isolated Flow 10 Compose tests passed on the Android emulator.
- Team review and passing CI are required before merge, per `docs/CONTRIBUTING.md`.
