# Issue #151 — Waiting-list integration and cleanup

> AI use: GPT-6 (OpenAI), through Codex, assisted me with issue #151.

Last updated: November 3, 2026.

Scope: [Issue #151](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/151),
following the merged owner waiting-list work in PR #148.

## Acceptance and implementation

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Open the list from Flow 4 | The merged owner lottery details route pushes the selected lottery's waiting list. Back pops to those details; the accessible label now says so. | Existing navigation is checked in `LambdaCampsiteApp`; the screen test checks the Back action. |
| Shared colors and strings | The waiting-list screen uses the shared Material color scheme, including its scrim and on-primary header text. Flow 12 strings live with the shared `owner_waiting_list_` resource family. | Debug Kotlin and Android-test compilation. |
| Incremental contacts | The controller keeps current authorized contacts, requests only missing entrant IDs after live updates, and drops departed contacts or all contacts on lottery/ownership change. | Controller tests cover join/leave, failed fetch with retry, and role loss. |
| Firebase setup | Firebase Functions uses the version catalog and normal `Firebase.functions` imports in `AppContainer`. The deployed callable uses the standard Firestore client; emulator configuration stays in the test fixture. | Android compilation and callable emulator tests. |
| Contact sheet | The read-only Figma sheet's title, contact rows, spacing and surface are used. Labeled copy controls are added for available email and phone values. | Compose tests check displayed details, copy actions and empty contact behavior. |
| Architecture | The existing Functions and MVC sections describe the contact boundary and cache lifecycle. An editable [Mermaid class-diagram source](images/class-diagram.mmd) documents this focused boundary. | Documentation review. |

## Figma alignment

The [team's Figma file](https://www.figma.com/design/usfTYZWNV25DRKPhZJjPdE/Lambda-Campsite-UI?node-id=59355-5860)
was inspected read-only. The waiting-list frame is `59355:5791`, the contact
sheet is `59355:5860`, and the empty state is `59355:5935`. The sheet follows
the mint surface, 28dp top corners, drag handle, 24dp horizontal padding and
title/contact/joined hierarchy. Copy icons extend the mockup to meet the issue's
explicit copy-action requirement.

## Local verification

- All 231 debug unit tests passed, including the targeted controller regressions.
- All five Flow 12 Compose tests passed on the Android 17 emulator; debug Android-test Kotlin compiled.
- The Python callable passed syntax compilation. Its pytest suite was not run locally because pytest and the Firebase CLI emulator are not installed in this workspace.
- Team review and passing CI are required before merge, per `docs/CONTRIBUTING.md`.
- Shared-project verification of the deployed callable needs the team's Firebase
  environment and an owner device; the emulator tests cover the access rules locally.
