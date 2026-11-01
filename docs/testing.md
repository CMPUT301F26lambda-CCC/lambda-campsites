# Testing Guide

> AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
> write this guide in October 2026, following the checklist in issue #63
> and the decisions in `docs/architecture.md`. It also helped update the
> CI section to match the workflow, and helped add the controller testing
> section when the controllers became ViewModels, and helped update the
> setup steps when the Firebase emulators were added, and helped add the
> part about rules test helpers and the repository test locations, and
> helped add the note about the shared Firestore cache with the campsite
> tests, and the note on unused IDs with the availability tests. The team
> reviewed and edited it in its pull requests.

This document is for everyone writing code in this app. It covers
which tests each user story needs, where test files go and what to
call them, how to run the tests on your machine, and how they run in
CI. The architecture these tests rely on, like fake repositories and
the MVC layers, is described in `docs/architecture.md`.

## Kinds of tests

We write three kinds of tests, and each one checks a different layer.

- **Unit tests** live in `app/src/test` and run on your computer
  without an emulator, in a few seconds. They cover model rules, like
  `Availability.isFree`, and controllers, which get a fake repository
  instead of Firestore. "Testing a controller" below shows how.
- **UI tests** live in `app/src/androidTest` and run on an emulator or
  a phone. They use Compose's testing tools to tap through a screen
  and check what it shows.
- **Intent tests** are UI tests that check the app hands off to
  another app correctly, like opening the photo picker or the share
  sheet for a CSV file. They use Espresso-Intents to check which intent
  was sent, without opening the other app.

Two more kinds cover the backend:

- **Security rules tests** also live in `app/src/androidTest`. Each one
  tries a write the rules should block, against the Firebase emulator,
  and expects a permission error.
- **Cloud Functions tests** live in `functions/tests` and run with
  pytest against the Firebase emulator.

No test ever touches our real Firebase project. Unit tests use fakes,
and everything else uses the Firebase Local Emulator Suite.

## Which tests each story needs

Every story gets three things:

1. A unit test for each rule in its acceptance criteria, including the
   case where the rule says no.
2. A controller unit test for each user action, covering both the
   success path and the error the user would see.
3. One UI test for the story's main path, if the story has a screen.
   If the story hands off to another app, that UI test is an intent
   test.

Stories that change who can read or write data also get a security
rules test. Stories that depend on a Cloud Function get a functions
test.

Some examples:

| Story | Unit tests | UI, intent or rules tests |
|---|---|---|
| 01.02.01 Join a waiting list | `Lottery` refuses a join after registration closes and when the list is full. `LotteryController.onJoin` reports each error. | Joining from `LotteryScreen` shows "You're on the waiting list". |
| 01.04.02 Choose dates after winning | `Availability.isFree` for overlapping, touching and separate date ranges. The controller reports "dates taken" when the transaction fails. | Picking dates and tapping Book ends on the confirmation. |
| 01.05.02 Confirmation by CSV | `ConfirmationExport` builds the right rows for a reservation. | Intent test: tapping "Save as CSV" sends a save or share intent. |
| 01.10.01 Opt out of notifications | `Profile.setNotifications` changes the setting. `sendPush` skips the push when it's off (functions test). | Turning the switch off shows the snackbar and stays off after reopening the screen. |
| 02.01.02 Provide campsite details | `Campsite.validate` rejects a missing name, a missing location and an occupancy of 0. | Intent test: tapping "Add photo" opens the photo picker. |
| 02.04.03 Registration period | `Lottery.validateDates` rejects a closing date before the opening date. | Saving bad dates shows the error under the date field. |
| 03.07.01 Remove an owner | `AdminModeration` removes the OWNER role and sets `ownerBanned`. | Rules test: an owner can't clear their own `ownerBanned`. |

## Where test files go and what to call them

Test files mirror the package of the class they test.

| What | Folder | File name |
|---|---|---|
| Unit test for a model class | `app/src/test/java/com/lambda/campsite/model/` | `LotteryTest` |
| Unit test for a controller | `app/src/test/java/com/lambda/campsite/controller/` | `LotteryControllerTest` |
| Fake repository | `app/src/test/java/com/lambda/campsite/model/repository/` | `FakeLotteryRepository` |
| Unit test for a repository's Firestore fields | `app/src/test/java/com/lambda/campsite/model/repository/` | `ProfileFieldsTest` |
| Firestore repository test | `app/src/androidTest/java/com/lambda/campsite/model/repository/` | `FirestoreProfileRepositoryTest` |
| Shared test helper | `app/src/test/java/com/lambda/campsite/testutil/` | `MainDispatcherRule` |
| UI or intent test for a screen | `app/src/androidTest/java/com/lambda/campsite/view/screens/` | `LotteryScreenTest` |
| Security rules test | `app/src/androidTest/java/com/lambda/campsite/rules/` | `ProfileRulesTest` |
| Cloud Functions test | `functions/tests/` | `test_draw_lotteries.py` |

Name each test after the behaviour it checks, so a failing test tells
you what broke without opening it.

- In unit tests, use a sentence in backticks, like a test called
  `join fails when registration has closed`.
- In `androidTest`, use camelCase, like
  `joinShowsWaitingListMessage`. Android versions before 11 can't run
  test names that contain spaces.

Each test checks one behaviour and follows the same three steps: set
up the data, do the one action, then check the result. Put the story
ID in the test class's KDoc, like "Tests for US 01.02.01", so we can
see which stories have tests.

## Testing a controller

Controllers are Jetpack ViewModels, and they run their work on
Android's main thread. Unit tests don't have one, so every controller
test class adds `MainDispatcherRule` from `testutil` as a JUnit rule.
With the rule in place, a controller finishes its work before its
method returns. The test calls the method, then checks
`controller.state.value` straight away.

Give the controller a fake repository. A fake keeps its data in
memory, records every save, and can be told to fail the way an offline
phone would. To test data that changes somewhere else, like an admin
banning an owner, the fake also has a method that changes its data
without a save. `FakeProfileRepository` has all three.

For each action, test the success path, every error the user can see,
and that a failed save leaves the state as it was. `ProfileControllerTest`
follows this pattern, so copy it when you start a new controller test.

## Running the tests locally

### Unit tests

In Android Studio, right-click `app/src/test` and choose "Run Tests".
From a terminal at the repo root, run `./gradlew testDebugUnitTest`.
Unit tests need no emulator and no internet connection.

### UI, intent and rules tests

Before your first run, install the Firebase CLI with
`npm install -g firebase-tools` and sign in with `firebase login`,
using the Google account you were added to the Firebase project with.
The emulators also need Java 21 or newer.

1. Start an Android emulator from Android Studio's Device Manager.
2. Start the Firebase emulators from the repo root with
   `firebase emulators:start`. The Emulator UI at
   `http://localhost:4000` shows the users and data the tests create.
3. Run `./gradlew connectedDebugAndroidTest`, or right-click
   `app/src/androidTest` in Android Studio and choose "Run Tests".

The test runner, `testutil/LambdaTestRunner`, points Firestore, Auth
and Storage at the emulators before the app starts. The Android
emulator reaches them at `10.0.2.2`. The emulators don't use HTTPS, so
debug builds allow plain HTTP to that one address. Android 17 also
blocks apps from local addresses like that one unless they have the
local network permission, so the runner grants it to debug builds.

Every test that uses Firestore calls `awaitSignIn()` from `testutil`
in a `@Before` method. The app signs in when it starts, and Firestore
keeps a separate queue of writes for each user, so a write made before
sign-in finishes never reaches the emulator.

Each test that uses Firestore also calls `FirestoreEmulator.clear()`
from `testutil` first, so tests don't depend on each other or on the
order they run in. To set up data a user isn't allowed to write, like
a banned owner or a removed profile, use
`FirestoreEmulator.setIgnoringRules`. It skips the security rules, the
way an admin's or a Cloud Function's writes do.

The phone's Firestore cache is shared by every user and lasts between
tests. A query or listener can send documents left over from an earlier
test before the server's answer arrives. Wait for the value you expect,
or for a snapshot whose `metadata.isFromCache` is false.
`FirestoreCampsiteRepositoryTest` and `FirestoreFlowsTest` show both.
For a single document, you can also give it an ID no other test uses,
like `watched` in the calendar tests, so the cache has no old copy of it.

`ProfileRulesTest` shows the pattern for rules tests. A few tests check
that normal use is allowed, so the rules can't pass by denying
everything. Then there's one test per rule, each trying something a
modified app might do and expecting `PERMISSION_DENIED`.

If a test fails with "No answer from the Firestore emulator", the
Firebase emulators aren't running.

### Cloud Functions tests

Only the person working on functions needs Python set up. From the
repo root, make the virtual environment once with
`python3.13 -m venv functions/venv`, then install the libraries with
`functions/venv/bin/pip install -r functions/requirements.txt`.
`firebase.json` runs the functions on Python 3.13, so use the same
version on your machine.

With the Firebase emulators running, run `pytest` inside `functions/`.

### Before opening a pull request

Run all unit tests, and the UI tests for any screen you changed. A
pull request that breaks an existing test doesn't get merged.

## Tests in CI

The CI pipeline is `.github/workflows/android-ci.yml`. It runs on
GitHub Actions on every pull request to `main`, on every push to
`main`, and when someone starts it by hand from the Actions tab.

- It builds the app with `./gradlew assembleDebug` and runs all unit
  tests with `./gradlew testDebugUnitTest`.
- If the build or any test fails, the check on the pull request turns
  red and the run keeps the test report, so you can see which test
  failed. `main` requires this check to pass, so the pull request
  can't be merged until it's fixed.
- UI tests aren't in CI yet. They need an Android emulator and the
  Firebase emulators, which makes them slow, so they'll get their own
  job that runs on a schedule and by hand once the first UI tests
  exist. Until then, run them locally before opening a pull request
  that changes a screen.

## Things to avoid

- Don't test against the real Firebase project. Use fakes or the
  emulators.
- Don't use fixed sleeps to wait for the UI. Compose's test tools wait
  for the screen to settle, and `waitUntil` covers anything slower.
- Don't find UI elements by test tags when a label works.
  `docs/accessibility.md` explains why labels matter, and finding
  elements by the label a screen reader would read checks both at
  once.
- Don't share data between tests. Each test sets up what it needs.
