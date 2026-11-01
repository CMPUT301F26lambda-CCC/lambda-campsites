# Glossary

> AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
> write this glossary in October 2026, collecting the terms used in the
> team's architecture, testing and documentation guides and in the
> starter code. It also helped add the BoM, Google services plugin and
> Task rows when the app was connected to Firebase, and helped update the
> repository rows when the profile moved to Firestore, and helped add the
> merge write row and the note on queries in the security rules row with the
> campsite repository, and helped add the availability calendar and
> LocalDate rows. Sashreek reviewed and edited it.

This page explains the technical words in our docs and code: the app's structure, Kotlin and Android, Firebase, and testing and tooling. Each section is in alphabetical order. Campsite words like entrant, lottery and waiting list are defined in the spec's glossary. The last section only covers meanings the team decided for itself.

Where a term has a longer explanation elsewhere, the entry points to it. "Architecture" means `docs/architecture.md`.

## Contents

1. [How our app is built](#how-our-app-is-built)
2. [Kotlin and Android](#kotlin-and-android)
3. [Firebase and the backend](#firebase-and-the-backend)
4. [Testing and tooling](#testing-and-tooling)
5. [Project terms we defined](#project-terms-we-defined)

## How our app is built

| Term | Meaning |
|---|---|
| **AppContainer** | The class that creates every repository once and hands it out. It's owned by `LambdaApplication`, so it lasts as long as the app does. It's also the only place that decides which version of a repository the app uses, so moving from in-memory data to Firestore means changing this file and nothing else. Architecture section 5. |
| **Application class** | A class Android creates once, before any screen, and keeps until the app closes. Ours is `LambdaApplication`, and it's registered in `AndroidManifest.xml`. It owns the `AppContainer`. |
| **Controller** | The part of a feature that handles what the user does. In our app each controller is a ViewModel, like `ProfileController`. It holds the screen's UI state, has one `onSomething` method per user action, checks rules with the model, and calls repositories. It never draws anything. Architecture section 5. |
| **Dependency injection** | Giving a class the objects it needs from outside, instead of letting it create them. A controller takes its repository in its constructor, so the app can give it the real repository and a test can give it a fake. We do this by hand with `AppContainer` and each controller's `Factory`, without a library. |
| **Domain class** | A class from the class diagram that describes something in the app's world, like `Profile`, `Campsite` or `Lottery`. Domain classes live in the `model` folder and use plain Kotlin, with no Android screen code. |
| **Factory** | The recipe Android uses to build a ViewModel that needs constructor arguments. Each controller has one, `XController.Factory`, which takes the controller's repositories from `AppContainer`. Android runs it only the first time a screen asks for the controller. |
| **Fake repository** | A simple working version of a repository used only in unit tests, like `FakeProfileRepository` or `FakeCampsiteRepository`. It keeps data in memory, records what was saved, and can be told to fail like an offline phone. See the testing guide. |
| **In-memory repository** | A repository that keeps its data in memory instead of Firestore, like the `InMemoryProfileRepository` the app used before the profile moved to Firestore. It lets a feature run before its Firestore version exists. The data is lost when the app closes. Each one gets replaced by its Firestore version in `AppContainer` and then deleted. |
| **Model** | The layer that holds the app's data and rules: domain classes, repositories and services. It has no screen code, so we can unit test it without a phone. Architecture section 5. |
| **MVC (Model, View, Controller)** | The way we split the app into three layers: the View shows things, the Controller handles taps, and the Model holds the data and rules. We use the layers from the course's MVC lecture, built from Android's own parts. Android's docs call this arrangement MVVM. Architecture section 5. |
| **Repository** | A class in the Model layer that loads and saves one kind of data, like `ProfileRepository`. It turns Firestore documents into Kotlin objects and back, runs transactions, and sends live updates as a Flow. Each repository is an interface, so controllers don't know or care whether the data comes from Firestore, memory or a test fake. |
| **Service** | A Model class that works across several kinds of data instead of holding data itself, like `CampsiteSearch` or `AdminModeration`. Services call repositories. |
| **UI state** | A data class with everything one screen shows, like `ProfileUiState`. It holds model data, like the profile, and screen-only values, like the active role or an error to show. The controller keeps it in its `state` property, and the screen reads it from there. It's named `XUiState` and lives in the controller's file. |
| **View** | The layer that draws the screens. Each screen is a composable function, like `ProfileScreen`, that reads its controller's state and calls the controller when the user taps something. Views never call repositories and never hold business rules. |

## Kotlin and Android

| Term | Meaning |
|---|---|
| **Activity** | One window that Android starts and manages. Our app has a single activity, `MainActivity`, and Compose navigation switches screens inside it. |
| **Back stack** | The history of screens the back button walks through. Switching roles in our app starts a fresh back stack, so you land on the new role's first tab. |
| **BoM (bill of materials)** | A list of library versions that are tested together. We add the BoM once with a version, then add its libraries without version numbers, so they always match. We use the Compose BoM and the Firebase BoM. |
| **Collecting** | Listening to a Flow and running some code for each value it sends. A controller collects its repository's Flow, and a screen collects its controller's state with `collectAsStateWithLifecycle()`. |
| **collectAsStateWithLifecycle()** | How a screen reads a StateFlow. It turns the flow into Compose state, so the screen redraws when the value changes. It stops listening while the app is in the background, so nothing updates a screen nobody can see. |
| **Composable** | A Kotlin function marked `@Composable` that describes part of the screen. `ProfileScreen`, `Text` and `Button` are all composables. Composables call other composables to build up a screen. |
| **Configuration change** | When the phone rotates, switches to dark mode or changes language, Android rebuilds the activity. Anything the activity owned is lost. ViewModels survive it. |
| **copy()** | A function every Kotlin data class gets. It makes a new object with some values changed and the rest kept, like a profile with one more role. We use it because our model objects are immutable. |
| **Coroutine** | A piece of work that can pause, for example while waiting for Firestore, without freezing the screen. Controllers start coroutines with `viewModelScope.launch`. |
| **Data class** | A Kotlin class marked `data` that mainly holds values. Kotlin writes `equals`, `toString` and `copy()` for it. Our domain classes and UI states are data classes. |
| **Dispatcher** | Decides which thread a coroutine runs on. `Dispatchers.Main` is Android's main thread, the one that draws the screen. Unit tests replace it with a test dispatcher, which is what `MainDispatcherRule` does. |
| **Dynamic colour** | An Android 12 feature that colours apps to match the wallpaper. We turned it off, so the app always shows the brand greens. |
| **Enum (enumeration)** | A type with a fixed list of values, like `Role` with ENTRANT, OWNER and ADMIN. Firestore stores enums by name, like `"OWNER"`. |
| **Flow** | A stream of values that arrive over time. A repository returns live data as a Flow: it sends the current data, then sends again every time the data changes. Nothing happens until something collects it. |
| **Gradle** | The tool that builds the app. It reads `build.gradle.kts`, downloads the libraries the app uses, compiles the code and runs the tests. |
| **Gradle sync** | Android Studio reading the Gradle files and downloading any new libraries. After someone adds a library, the code that uses it shows up red until you sync. |
| **Immutable** | Can't change after it's made. Our domain classes and UI states are immutable: every property is a `val`, and a change makes a new copy. This matters because a StateFlow only sends a value that's different from the last one. |
| **Interface** | A list of functions a class promises to have, without the code for them. `ProfileRepository` is an interface, and `FirestoreProfileRepository` and the test fake `FakeProfileRepository` both implement it. |
| **Jetpack** | Google's set of Android libraries. We use Jetpack Compose for screens, Lifecycle for ViewModels and Navigation for moving between screens. |
| **Jetpack Compose** | Android's toolkit for building screens in Kotlin code instead of XML layouts. You describe what the screen should show for the current state, and Compose redraws it when the state changes. |
| **lazy** | A Kotlin property written `by lazy { ... }` is created the first time someone reads it, and the same object comes back every time after. `AppContainer` creates repositories this way. |
| **Lifecycle** | The stages a screen goes through: created, visible, in the background, destroyed. Lifecycle-aware code, like `collectAsStateWithLifecycle()`, starts and stops itself at the right stage. |
| **Live region** | An accessibility setting that makes TalkBack read text out as soon as it appears, without the user moving to it. Our error messages use it. See `docs/accessibility.md`. |
| **LocalDate** | Java's type for a date with no time, like July 2, 2027. We use it for season and stay dates, so a time zone can't move a date to the day before. Firestore has no such type, so these dates are stored as text like `2027-07-02`. |
| **Main thread** | The one thread that draws the screen and handles taps. Anything slow on it freezes the app, so network calls and photo shrinking run in coroutines instead. |
| **Manifest** | `AndroidManifest.xml`, the file that tells Android about the app: its Application class, its activities, its permissions and its name. |
| **Material 3** | Google's design system for Android, which Compose's buttons, chips and nav bars follow. Our Figma file uses the Material 3 kit. |
| **Navigation, NavHost and route** | Compose Navigation moves between screens. The `NavHost` holds every screen, each screen has a `route` string like `"owner/campsites"`, and the nav controller opens a screen by its route. |
| **Process death** | Android closes an app that's been in the background a while to free memory. ViewModels don't survive it, so the app reopens with fresh state, which for us means as an entrant. |
| **Recomposition** | Compose running a composable again because state it reads has changed. This is what "Compose redraws the screen" means in our docs. Compose only re-runs the parts that read the changed value. |
| **Semantics** | Extra information on a composable for accessibility tools, like marking text as a heading so TalkBack can jump between headings. See `docs/accessibility.md`. |
| **StateFlow** | A Flow that always holds one current value. New collectors get that value straight away, and it only sends a new value when it's different from the old one. Each controller's `state` is a StateFlow. |
| **String resource** | Text kept in `res/values/strings.xml` instead of typed into Kotlin, like `R.string.tab_home`. Every piece of text the user sees goes there. |
| **suspend function** | A function that can pause without freezing the screen, for example while waiting for Firestore. It can only be called from a coroutine. Repository writes like `save()` are suspend functions. |
| **TalkBack** | Android's screen reader, which reads the screen aloud for blind and low-vision users. See `docs/accessibility.md`. |
| **Theme** | The colours, text styles and shapes every screen uses, defined in `view/theme`. Screens take colours from the theme instead of typing in hex values, which is how dark mode works everywhere. |
| **Version catalog** | `gradle/libs.versions.toml`, the one file that lists every library and its version. `build.gradle.kts` refers to libraries by their catalog name. |
| **ViewModel** | A Jetpack class that holds a screen's data and survives configuration changes like rotation. Android clears it once its screen is gone for good. All our controllers extend ViewModel. |
| **viewModelScope** | The coroutine scope that belongs to a ViewModel. Work started in it is cancelled automatically when Android clears the ViewModel, so it can't keep running for a screen that's gone. |

## Firebase and the backend

| Term | Meaning |
|---|---|
| **Anonymous sign-in** | A Firebase Auth mode that gives each app install a user ID without a login screen. It's how we identify a device for US 01.09.01. Uninstalling the app or clearing its data gives a new ID. |
| **Blaze plan** | Firebase's pay-as-you-go plan. It still includes the free quotas, and we stay inside them. We need it because Cloud Functions and Cloud Storage don't run on the free Spark plan. Architecture section 4. |
| **Cloud Firestore** | Firebase's database, and where all our app data lives. Data is stored as documents in collections. Architecture section 4 shows our layout. |
| **Cloud Functions** | Code that runs on Google's servers instead of a phone. Ours are written in Python, in `functions/main.py`. We use them for work no phone should or can do, like the lottery draw and sending pushes. Architecture section 4. |
| **Cloud Scheduler** | The Google service that starts a function on a timer. It runs `drawLotteries` every 5 minutes. |
| **Collection** | A group of documents in Firestore, like `campsites` or `reservations`. |
| **Collection group query** | A Firestore query across every subcollection with the same name, wherever it sits. We use one on `entries` to find every waiting list one person is on. |
| **Composite index** | An index Firestore needs for a query that filters or sorts on more than one field. Indexes are listed in `firestore.indexes.json`. Without the right index, the query fails with an error that links to the console. |
| **Document** | One record in Firestore: a set of fields, like a single campsite or a single reservation. Every document has an ID, and the full path, like `campsites/abc123`, finds it. |
| **Document ID** | The name of a document within its collection. Sometimes we choose it on purpose to enforce a rule. A rating's ID is the rater's user ID, so each person can only have one rating per campsite. |
| **Firebase** | Google's platform of backend services. We use Auth, Firestore, Storage, Cloud Functions and Cloud Messaging. |
| **Firebase Auth** | The Firebase service that signs users in and gives each one a user ID (UID). We only use anonymous sign-in. |
| **Firebase Cloud Messaging (FCM)** | The service that delivers push notifications to phones. The `sendPush` function sends through it. |
| **Firebase Local Emulator Suite** | Local copies of Firestore, Auth, Storage and Functions that run on your computer. UI, rules and functions tests run against them, so no test ever touches the real project. |
| **Firebase Storage** | Where photo files live. Firestore only keeps each image's details and its path in Storage. |
| **Free quota** | The amount of each Firebase service you can use each day or month at no cost. Architecture section 4 lists them next to our expected usage. |
| **Google services plugin** | The Gradle plugin that reads `app/google-services.json` during the build and turns it into settings Firebase reads when the app starts. The build fails if the file is missing. |
| **google-services.json** | The file that connects the app to our Firebase project. It's safe to commit, because it only identifies the project, and security rules protect the data. |
| **Idempotent ("safe to run twice")** | Code that does the same thing whether it runs once or twice. Firebase can deliver the same trigger more than once, so every function checks the current state first. The draw only draws an OPEN lottery, so a second run does nothing. |
| **Merge write** | A Firestore write with `SetOptions.merge()`. It changes only the fields it sends and keeps the rest of the document. `FirestoreCampsiteRepository.save` uses one, so it never overwrites the rating totals that only a Cloud Function writes. |
| **Offline cache** | Firestore keeps a copy of the data each screen has loaded on the phone. Without a connection, screens still show it, and ordinary writes wait and go through later. Transactions can't wait, so they fail offline. |
| **Push notification** | A message that appears on the phone even when the app is closed, like "You won a spot". Each one also exists in the app's inbox as a notification document. |
| **Push token** | An ID for one app install that FCM uses to deliver pushes to that phone. It's stored on the profile as `pushToken` and can change. |
| **Security rules** | Rules in `firestore.rules` and `storage.rules` that Google's servers check on every read and write from the app. They stop a modified app from skipping our checks, for example adding ADMIN to its own roles. Cloud Functions use admin access, which skips these rules, so functions must do their own checks. Rules don't filter queries: Firestore refuses a whole query if it could return a document the user can't read, so a query must ask only for what the rules allow, like published campsites. Architecture section 4. |
| **Server timestamp** | A time set by Firestore's servers instead of the phone's clock. We use it for `joinedAt` and `sentAt`, so a phone with the wrong clock can't jump the queue. |
| **Snapshot listener** | A Firestore feature that sends a document's or query's data straight away and then again every time it changes. Our repositories put one behind each Flow, which is how a change on one phone shows up on another. They use `liveSnapshots()` from `model/repository/FirestoreFlows.kt`, which ends the Flow with an error if the listener fails, so the screen can show a message. |
| **Subcollection** | A collection inside a document, like `campsites/{id}/ratings`. Deleting the document doesn't delete its subcollections, so our clean-up functions do it. |
| **Task and await()** | Firebase calls like signing in or saving a document return a `Task`, which finishes later. Inside a suspend function, `.await()` waits for the task without blocking the main thread, then returns its result or throws its error. It comes from `kotlinx-coroutines-play-services`. |
| **Transaction** | A set of Firestore reads and writes that succeed or fail together. If another phone changes one of the documents first, Firestore runs the whole transaction again with fresh data. We use them for joining, booking, cancelling and the draw, so two people can never take the same spot or night. Architecture section 4. |
| **Trigger** | What starts a Cloud Function: a timer, or a document being created, changed or deleted. |
| **UID** | The user ID Firebase Auth gives each install. Profile documents use it as their document ID. |

## Testing and tooling

| Term | Meaning |
|---|---|
| **Branch protection** | GitHub settings that guard `main`, like requiring a review and a passing CI check before a pull request can be merged. |
| **CD (continuous delivery)** | Automatically turning merged code into something people can use, like an APK or a deployed function. We haven't set it up yet. |
| **CI (continuous integration)** | Building the app and running the tests automatically on every pull request and every push to `main`. Ours runs on GitHub Actions. See the testing guide. |
| **Conventional commit** | A commit message that starts with its type, like `feat:`, `fix:`, `docs:`, `test:` or `refactor:`. See `docs/CONTRIBUTING.md`. |
| **Dokka** | JetBrains' tool that turns KDoc comments into a website. Ours is published at https://cmput301f26lambda-ccc.github.io/lambda-campsites/. Run `./gradlew :app:dokkaGenerate` to build it on your computer. See `docs/documentation.md`. |
| **Espresso-Intents** | A testing library that checks which intent the app sent, like opening the photo picker, without opening the other app. |
| **Fake** | A simple, working stand-in for a real class, used in tests. We use fakes, not mocks. A mock is scripted with answers and checks which calls it got. A fake just behaves like the real thing in memory. |
| **GitHub Actions** | GitHub's service for running workflows, like our CI, on GitHub's computers. |
| **GitHub Pages** | GitHub's free website hosting for a repo. The Docs workflow publishes our Dokka site there after every merge to `main`. |
| **Intent** | An Android message asking another app or screen to do something, like pick a photo or share a CSV file. |
| **Intent test** | A UI test that checks the app sends the right intent. See the testing guide. |
| **JUnit** | The testing framework our unit tests use. A test is a function marked `@Test`. |
| **JUnit rule** | Code that runs before and after every test in a class. `MainDispatcherRule` is one. |
| **KDoc** | Kotlin's documentation comments, written `/** ... */` above a class or function. See `docs/documentation.md`. |
| **MainDispatcherRule** | Our JUnit rule, in `test/.../testutil`, that lets unit tests run controllers. ViewModels run on Android's main thread, which unit tests don't have, so the rule swaps in a test dispatcher. Every controller test uses it. See the testing guide. |
| **Pull request (PR)** | A request to merge a branch into `main`. It needs one approval and a passing CI check, and it closes its issue with "Closes #N" in the description. |
| **Required status check** | A CI check that must pass before GitHub allows a merge. Ours is "Build and unit tests". |
| **Rules test** | A test that tries a write the security rules should block, against the emulator, and expects it to fail. |
| **Stacking** | Starting a new branch from another branch that hasn't been merged yet, so you can keep working while the first one waits for review. |
| **Test dispatcher** | A dispatcher for tests that runs coroutines on the test's own thread. `UnconfinedTestDispatcher` runs them straight away, so a controller's work is done before its method returns. |
| **UI test** | A test that runs on an emulator or phone and taps through real screens, in `app/src/androidTest`. It's slower than a unit test. |
| **Unit test** | A test that runs on your computer without an emulator, in `app/src/test`, in a few seconds. It covers model rules and controllers. |
| **Workflow** | A GitHub Actions file in `.github/workflows` that says what to run and when. Ours are `android-ci.yml`, which builds and tests every pull request, and `docs.yml`, which publishes the Dokka site. |

## Project terms we defined

These are meanings the team decided where the spec was unclear, or names from our own design. Architecture section 3 has the reasons.

| Term | Meaning |
|---|---|
| **Active role** | The role someone is using the app as right now, chosen with the "Use the app as" chips. It picks the nav bar. It's screen state, not saved to the profile. |
| **Availability calendar** | A campsite's season and booked nights, stored in `campsites/{id}/availability/calendar`. The owner sets the season. A stay covers the nights from check-in up to check-out, so one guest can check in on the day another checks out. |
| **Booking period** | The stay dates a lottery covers, `stayStart` to `stayEnd`. Winners book free nights inside it. |
| **Completed stay** | A confirmed reservation whose check-out date has passed. It's worked out from the date, not stored as a status. Only entrants with a completed stay can rate a campsite. |
| **Conversation** | The chat between an owner and an entrant about one reservation. There's one per reservation, and it's created when the chat screen first opens. |
| **Draw** | The `drawLotteries` function picking winners at random. It runs within 5 minutes of registration closing, with no phone involved. |
| **Entry** | One person's place on a lottery's waiting list, stored in `lotteries/{id}/entries/{uid}`. Its status goes from WAITING to WON or NOT_SELECTED, then to BOOKED or DECLINED. |
| **Owner ban** | The `ownerBanned` flag an admin sets when removing someone's owner role (03.07.01). While it's set, the person can't become an owner again. |
| **Registration window** | The time between `registrationOpens` and `registrationCloses` when entrants can join a lottery's waiting list. |
| **Removed profile** | A profile an admin removed (03.02.01). It stays in Firestore with `removed` set and its personal details wiped, so the same device can't start over. A user who deletes their own profile (01.08.01) gets a full delete instead. |
