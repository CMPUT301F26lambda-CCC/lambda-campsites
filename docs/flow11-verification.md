# Flow 11 — Owner's reservations

> AI use: GPT-6 (OpenAI), through Codex, assisted me with Flow 11.

Last updated: November 3, 2026.

Scope: [flow #128](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/128)
and owner stories #31 to #34. The existing reservation backend from #108 is reused.

## Acceptance criteria and evidence

| Story | Requirement | Implementation and verification |
| --- | --- | --- |
| #31 | View booked dates for one owned campsite, switch months, open a booked night, and see live changes or the exact empty message | The campsite details action opens `OwnerBookedDatesScreen`. `OwnerBookedDatesController` combines campsite, availability and reservation listeners. Its unit tests cover month navigation and cancellation. The Compose tests cover selection and the empty message. The real app emulator test opens a booked night and observes the calendar after cancellation. |
| #32 | View active reservations across owned campsites, with campsite, entrant, dates and full details | Owner Home shows the Active tab. `Reservation.isActive` classifies confirmed future and ongoing stays. The real app emulator test verifies the owner-scoped query and details sheet. |
| #33 | View completed reservations only, with details | The Past tab uses `Reservation.isCompleted`; unit and Compose tests exclude active, cancelled and rejected stays. The real app emulator test opens the Past tab. |
| #34 | Retain cancelled reservations after their nights become free; show the exact empty message | The Cancelled tab filters `CANCELLED` records without reading the calendar. Unit and Compose tests cover grouping and the empty state. The real app emulator test checks a seeded cancelled reservation. |

## Code structure

The views only render state and call controller actions. Both controllers expose
`StateFlow` and obtain existing repository interfaces through their ViewModel
factories. `OwnerReservationDetailsSheet` is shared by the list and calendar.
No Firestore calls or booking rules were added to Compose. The Android app's
existing `AppContainer`, repository queries, security rules and navigation
patterns are reused. Flow 12's owner waiting-list route remains available
after rebasing this branch onto `main`.

## Local verification

- `assembleDebug`, `testDebugUnitTest` and `assembleDebugAndroidTest` passed after the rebase. The unit suite contained 177 tests with no failures.
- Four isolated Flow 11 Compose tests passed on the Android emulator.
- One real app Flow 11 test passed against local Auth and Firestore emulators. It covers owner navigation, active/past/cancelled lists, owner-only results, reservation details, a booked date and a live cancellation update.
- The real app test refuses any Firestore host except the local emulator and seeds only fictional profiles and campsites.

The Firebase tests require a running Android emulator and `firebase emulators:start --only auth,firestore` from the repository root. The team's PR review and CI still apply before this branch is merged into `main`. Manual TalkBack and physical-device review remain open.
