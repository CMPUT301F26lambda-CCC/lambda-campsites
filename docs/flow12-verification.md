# Flow 12 — Owner sees the waiting list

Last updated: 2026-11-03.
Outstanding issues: Callable deployment, populated shared-project verification,
manual TalkBack and physical-device review remain pending (#129, #36).

> Feature owner: Lalith Valluru. AI use: OpenAI GPT-6 (OpenAI), through Codex,
> assisted with implementation, tests and this verification record. Human review pending.

Scope: flow issue #129 and US 02.09.01 (#36). Secure owner-only contact access
satisfies the original story's name/contact requirements while keeping direct
profile reads private. This extends the earlier IDs-only limitation in #129.

## Acceptance criteria and evidence

| Story requirement | Implementation / automated evidence |
| --- | --- |
| Select a lottery for an owned campsite | Owner Lotteries tab opens a selector sourced from myLotteries; controller rejects foreign IDs. Real app navigation test selects between two owned lotteries. |
| Display entrant names | Authenticated callable returns minimal current contact projections; real app test checks Alex Demo and Jordan Demo. Missing names fall back to entrant ID. |
| Select entrant for basic name/contact information | Tapping a row opens the Figma-aligned sheet; integration test verifies email and phone, closes it and returns to the list. Missing values say Not provided. |
| Update automatically on joins/leaves | Existing Firestore waiting-list listener; integration test adds/removes an emulator registration and observes counts without refreshing. Controller tests verify ordering, removal and sheet dismissal. |
| Show No entrants on the waiting list. | Exact message and zero count tested after choosing an empty lottery; distinguished from loading/failure/no-lotteries. |
| Only own lotteries' waiting lists | Selector/controller guard plus existing Firestore ownership rules. Negative tests reject foreign list reads, private profile reads and foreign callable contact requests. Server tests reject inactive/banned/removed owners. |

## Design and behavior

Reference: Lambda Campsite UI → Sreeman → US 02.09.01 frames
59355:5791 (waiting list), 59355:5860 (contact sheet), 59355:5935 (empty).
Forest header, mint rounded content, lottery field, avatars, join dates, chevrons,
count and owner bottom navigation follow that reference. Text remains in resources.
IDs and explicit privacy/error text are added where needed for real data.
Lists and the contact sheet scroll; row heights grow with wrapped text.

## Local verification commands

Use the existing project test runner and a separate Android emulator. Never run
data-clearing tests against the shared Firebase project. LambdaTestRunner routes
Auth/Firestore/Functions to 10.0.2.2:9099/8080/5001; the Firebase test refuses another
Firestore host. All fixtures are fictional demo identities.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
firebase emulators:start --only auth,firestore,functions
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.lambda.campsite.view.screens.OwnerWaitingListScreenTest'
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.lambda.campsite.view.screens.OwnerWaitingListFirebaseTest'
functions/venv/Scripts/python.exe -m pytest functions/tests -q
```

Do not run Android Firebase and Python tests concurrently: both reset the local
Firestore database. Backend emulator tests use the locally installed Python 3.12;
the repository's deployment runtime stays Python 3.13. No deployment is included.

## Review and release

Final local run, November 3, 2026:

- Debug app and Android-test APK builds succeeded.
- 172 JVM tests passed on main including the merged Flow 7 recovery PR #147
  (including 13 new Flow 12 controller cases).
- 4 Flow 12 Compose tests passed, including long-list and contact-sheet scrolling
  at 200% font size and explicit missing-phone presentation.
- 2 real-app Firebase tests passed against local Auth/Firestore/Functions,
  including own-list access, names/email/phone, ordered rows, live joins/leaves,
  empty-state selection, Back, foreign-list/contact denial, private-profile denial
  and signed-out callable denial.
- 26 Python backend tests passed, including 13 contact authorization/validation
  cases and the existing automatic-draw/push cases. Existing FCM deprecation
  warnings remain; messaging sends are mocked in those tests.
- Default-size populated list, contact sheet and empty list screenshots were
  inspected. A confirmed light-status-bar-icon contrast problem on the modal
  was corrected and the app integration tests passed again.
- `git diff --check` passed. Existing AppTab annotation and rating-string resource
  warnings are outside this feature's changes.

- Human visual, TalkBack and physical-device review remains required.
- The team's deployment owner must deploy only the new callable after PR approval.
  Until deployed, the shared-project screen shows a retryable contact-loading error
  with registration IDs; it does not falsely claim names/contact are available.
- No direct profile access was granted and automatic drawing is unchanged.
- Shared-account testing is pending: the reviewer's current owner account shows
  the no-lotteries state. A populated shared-project lottery and the deployed callable
  are needed before confirming the release against shared Firebase.
- This feature does not deploy functions, close issues or write production test data.
