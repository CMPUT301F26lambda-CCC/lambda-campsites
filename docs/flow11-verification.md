# Flow 11 — Owner's reservations

> AI use: GPT-6 (OpenAI), through Codex, assisted me with Flow 11.

Last updated: November 3, 2026.

Scope: [flow #128](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/128)
and owner stories #31 to #34. The existing reservation backend from #108 is reused.

## Acceptance criteria and evidence

| Story | Requirement | Implementation and verification |
| --- | --- | --- |
| #31 | View booked dates for one owned campsite, switch months, open a booked night, and see live changes or the exact empty message | The campsite details action opens `OwnerBookedDatesScreen`. `OwnerBookedDatesController` combines campsite, availability and reservation listeners. Its unit tests cover month navigation, cancellation, the two-stream loading state, and season boundaries. The Compose tests cover selection, loading, date announcements, and the empty message. The real app emulator test opens a booked night and observes the calendar after cancellation. |
| #32 | View active reservations across owned campsites, with campsite, entrant, dates and full details | Owner Home links to the Active tab. `Reservation.isActive` classifies confirmed future and ongoing stays. The real app emulator test verifies the owner-scoped query and details sheet. |
| #33 | View completed reservations only, with details | The Past tab uses `Reservation.isCompleted`; unit and Compose tests exclude active, cancelled and rejected stays. Card and details sheet use the same completed status, checked by a Compose regression test. The real app emulator test opens the Past tab. |
| #34 | Retain cancelled reservations after their nights become free; show the exact empty message | The Cancelled tab retains `CANCELLED` records and also shows `REJECTED` records, as in the Figma mockup. Unit and Compose tests cover grouping and the empty state. The real app emulator test checks a seeded cancelled reservation. |

## Figma alignment

Reviewed the [Flow 11 frames in the team's Figma file](https://www.figma.com/design/usfTYZWNV25DRKPhZJjPdE/Lambda-Campsite-UI?node-id=59352-2620) read-only. The owner Home shortcuts follow frame `59354:2621`, booked dates and the day sheet follow `59355:2649` and `59355:2804`, and the three reservation states follow `59355:5352`, `59355:5415`, and `59355:5478`. The implementation uses the same owner colour tokens, rounded content surface, segmented list control, reservation card hierarchy, peach booked dates, legend, and local Figma icons. All 11 Flow 11 icons are committed in `app/src/main/res/drawable-nodpi/`; their PNGs were rendered from the exact Figma SVGs at 3× resolution to stay sharp at the Compose icon sizes. The existing `camp_logo.png` is also in the repository. Emulator screenshots of Home, Active, and Booked dates were visually compared with these frames.

The Figma sample campsite photo is illustrative. The current `ImageRepository` only supports upload, so reservation cards use the mockup's exact tent fallback icon until an image-read API exists. Home's lottery activity is outside Flow 11, so this PR only supplies its reservation and booked-date shortcuts. The mockup's full-screen rejection flow belongs to US 02.08.01; Flow 11 currently shows the owner-visible reservation details in a sheet.

## Code structure

The views only render state and call controller actions. Both controllers expose
`StateFlow` and obtain existing repository interfaces through their ViewModel
factories. `OwnerReservationDetailsSheet` is shared by the list and calendar.
No Firestore calls or booking rules were added to Compose. The Android app's
existing `AppContainer`, repository queries, security rules and navigation
patterns are reused. Flow 12's owner waiting-list route remains available
after merging the latest `main` into this branch.

## Local verification

- `assembleDebug`, `testDebugUnitTest` and `assembleDebugAndroidTest` passed after Figma alignment, merging the latest `main`, and the review fixes. The unit suite contained 184 tests with no failures.
- Eight isolated Flow 11 Compose tests passed on the Android emulator, including loading after a campsite switch, season and no-season announcements, and the completed status in the card and details sheet.
- One real app Flow 11 test passed against local Auth and Firestore emulators. It covers owner navigation, active/past/cancelled lists, owner-only results, reservation details, changing between two owned campsite calendars, a booked date and a live cancellation update.
- The real app test refuses any Firestore host except the local emulator and seeds only fictional profiles and campsites.

The Firebase tests require a running Android emulator and `firebase emulators:start --only auth,firestore` from the repository root. The team's PR review and CI still apply before this branch is merged into `main`. Manual TalkBack and physical-device review remain open.
