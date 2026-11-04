# Owner campsite follow-up (#150)

> GPT-6 (OpenAI), through Codex, assisted me with this implementation summary.
> Teammate review is pending.

The owner register and edit screens use the same form fields and follow the
read-only Figma register (`59277:1495`), edit (`59272:1517`), owner list
(`59400:134`) and details (`59271:965`) mockups. Figma was not changed.

The forms now support individually chosen season dates, price and rules,
image selection/upload/replace/remove, a map-pin preview, tags, and discard
confirmation. Saving still preserves an edited campsite's ID. The owner list
can reconnect its live listener after an error, and an edit load failure offers
retry. Owner details show the uploaded photo, related lotteries and a generated
QR preview. Profile role selection now switches the app's active role. The
shared app header no longer duplicates owner screen headers, and the map picker
uses its own navigation area. Register and edit saves show confirmation snackbars.
The duplicate AppContainer header was consolidated.
Existing owner Booked dates navigation is already
implemented, so its action remains available.

Photo removal unlinks the image from the campsite. Storage rules currently
allow client uploads but not deletes; removing an unused Storage object requires
a separate server-side cleanup policy.

Verification:

- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lambda.campsite.view.screens.OwnerCampsiteFormScreenTest --offline`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lambda.campsite.controller.OwnerCampsitePhotoTest --offline`
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lambda.campsite.view.screens.ProfileRoleSwitchTest --offline`

The controller tests cover price/rules persistence and validation, edit-load
retry, live list recovery and campsite-specific lotteries. The Compose tests cover field availability,
editing price/rules and discard confirmation on a local Android emulator.
The photo tests check uploaded image IDs on registration, replacement on edit,
removal of the link, and preservation of the campsite ID.
The Profile UI test checks that the Admin selection changes the active role.

## Review fixes

The owner Campsites tab uses the existing transparent `flow11_tent` asset;
`flow8_tent` had an opaque background that appeared as a tinted square.
Register and edit now display season dates with the same abbreviated-month
formatter as owner details. The owner campsite count, names and locations use
explicit dark Figma text tokens on the mint list surface, including when the
phone uses dark mode.

The owner form emulator test checks the exact displayed dates and the icon's
transparent corner. `OwnerCampsiteListContrastTest` checks the rendered text
colors under dark mode. Unit tests and both targeted emulator suites passed.
