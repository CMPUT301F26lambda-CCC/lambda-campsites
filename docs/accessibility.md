# Accessibility Guide

This document is for developers and designers working on this app. It
covers what accessibility means in this project and the concrete rules
every screen needs to follow.

## What accessibility covers

Accessibility means the app works for people regardless of how they
perceive or interact with a screen. That spans several distinct needs:

- **Vision**: blindness (no visual output at all, navigation through a
  screen reader), low vision (needs large text or high contrast, but
  can still see the screen).
- **Motor**: difficulty with precise taps, small targets, or gestures
  that require fine control.
- **Hearing**: audio cues or notifications need a non-audio equivalent.
- **Cognitive**: clear, simple language and predictable navigation
  reduce load for anyone, and are a requirement for some users rather
  than a nice-to-have.

## Scope of this document

This guide focuses on blind accessibility through Android's screen
reader, TalkBack. That's the category the team can reliably implement
correctly and verify directly, rather than guess at. Low-vision
contrast and text-size rules, and motor-accessibility rules like touch
target size, are included below since they overlap directly with the
TalkBack work. General cognitive-load guidance (plain language, simple
flows) applies throughout but isn't broken out into its own rule set
here.

Custom multi-finger gestures, binding a two- or three-finger swipe or
tap to an app-specific action, are out of scope for a different
reason: TalkBack reserves those gestures for its own navigation while
it's running, so an app can't rely on claiming them. Don't design a
feature around one; use a visible touch target or a custom
accessibility action instead.

## TalkBack rules

### Every actionable element needs a label

Any `Button`, `IconButton`, clickable `Row`, or similar element needs
either visible text TalkBack can read, or an explicit label if it's
icon-only:

```kotlin
IconButton(onClick = { /* ... */ }) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete campsite"
    )
}
```

An icon with `contentDescription = null` is invisible to TalkBack.
Only pass `null` for a purely decorative icon that sits next to text
already saying the same thing.

### `testTag` is not an accessibility label

`Modifier.testTag("deleteButton")` is read by UI tests, not by
TalkBack. The two serve different purposes and a screen needs both
where relevant: a `testTag` for automated tests, and a
`contentDescription` or visible text for TalkBack.

### Don't rely on color alone

A status shown only as a colored dot or colored text (for example a
reservation that's approved in green, pending in yellow, rejected in
red) has no non-visual signal at all, and TalkBack never reads a
color. There are two separate things to fix here:

For low vision, pair the color with a second *visual* cue: bold
weight, an icon, or underline, not color as the only differentiator.

For blind users, that second visual cue still isn't enough, since
TalkBack doesn't read bold weight or icons without their own label
either. The text itself (or its `contentDescription`) has to say the
status in words:

```kotlin
val style = if (isRejected) {
    SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
} else {
    SpanStyle(color = MaterialTheme.colorScheme.primary)
}
Text(
    text = buildAnnotatedString {
        withStyle(style) { append(if (isRejected) "Rejected" else "Approved") }
    }
)
```

The bold weight helps a low-vision user who can't rely on color alone;
the word "Rejected"/"Approved" is what actually makes it accessible to
a blind user, since that's the part TalkBack reads regardless of
styling.

### Group related content into one node

A campsite list item that renders the name, location, and rating as
three separate `Text` elements forces TalkBack to read them as three
separate stops, out of context from each other. Give the row a single
`contentDescription` summarizing the item ("Elk Island Site 4,
4.5 stars"), or use `Modifier.semantics(mergeDescendants = true)` on
the container so it reads as one unit.

### Use structural hierarchy TalkBack can navigate by

Content that's visually a heading, a list, or a table needs to be
marked that way in semantics too, not just styled to look like one.
TalkBack's reading controls can jump straight between headings, and
list semantics tell TalkBack how many items there are and where the
user currently is among them, something a column of unmarked `Text`
composables can't convey no matter how it's laid out on screen:

```kotlin
Text(
    text = "Amenities",
    modifier = Modifier.semantics { heading() },
    style = MaterialTheme.typography.titleMedium,
)
```

`LazyColumn`/`LazyRow` already carry list semantics automatically, so
a plain scrolling list of campsite cards needs nothing extra. Apply
the same idea anywhere a screen has real structure: a section title
should be marked as a heading, a set of results should read as a
list, not as a sequence of disconnected rows that happen to look
grouped.

### Selected state needs its own signal, not just a background color

A selected campsite in a browse list that's only shown with a
highlighted background has the same problem as a colored status: no
non-visual signal. `Modifier.selectable` (with `selectableGroup()` on
the container) already marks the row as selected on its own, which
TalkBack announces by itself. Don't also type the word "selected"
into `contentDescription`; that risks TalkBack saying it twice, once
from the state it already tracks and once from the literal text. If
the default wording needs to say something more specific, override it
through `stateDescription`, which is the property built for that,
keeping `contentDescription` as just the row's label:

```kotlin
LazyColumn(modifier = Modifier.selectableGroup()) {
    items(campsites) { site ->
        val isSelected = site == selectedSite
        Text(
            text = site.name,
            modifier = Modifier
                .selectable(selected = isSelected, onClick = { onSelect(site) })
                .semantics {
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
        )
    }
}
```

Android's documented example for a built-in `Switch` is announced as
"On; Switch; double tap to toggle", state before the control, which
would already put a row's selected state ahead of its name for
someone scrolling quickly. That's TalkBack's own formatting of
`stateDescription`, not something to control by reordering text, and
it's based on one documented example for a different component, not a
guaranteed rule for this exact row; confirm the actual order with
TalkBack on a device before relying on it.

### Images need alt text

Campsite photos uploaded by owners need a `contentDescription` that
says what the image actually is in context ("Photo of Elk Island
Site 4"), not a generic "image" or the filename. If a photo is
decorative only (for example a background texture), mark it
`contentDescription = null` instead of leaving a meaningless label.

### Minimum touch target size

Interactive elements should be at least 48dp in both dimensions, even
if the visible icon or text inside is smaller. Pad a small icon button
out to that size with `Modifier.size(48.dp)` rather than leaving the
tap target the same size as the icon. Leave at least 8dp between two
adjacent targets so a motor-impaired tap doesn't land on the wrong
one.

### Announce state changes

An action that changes something without navigating to a new screen
(adding a campsite to favorites, submitting a reservation) needs its
result announced, since there's no new screen title for TalkBack to
read. Mark the text showing the result as a live region so TalkBack
reads it on its own as soon as it changes, instead of requiring the
user to go find it:

```kotlin
Text(
    text = if (reservationResult.accepted) "Reservation confirmed" else "Reservation failed",
    modifier = Modifier.semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
    },
)
```

`mergeDescendants = true` matters when the live region wraps more than
one `Text`, such as a confirmation line plus a date range underneath
it; without it, TalkBack only announces the first line and treats the
rest as separate, unannounced stops.

A slider or similar control that reports a raw percentage by default
(for example a party-size or date-range picker) should override that
with a `stateDescription` that actually means something:

```kotlin
Slider(
    value = partySize.toFloat(),
    onValueChange = { partySize = it.roundToInt() },
    valueRange = 1f..8f,
    modifier = Modifier.semantics {
        contentDescription = "Party size: $partySize"
        stateDescription = "$partySize"
    },
)
```

### Expose secondary actions as custom accessibility actions

A row that already has a primary tap action (opening a campsite's
details) sometimes needs a second action too, like favoriting it or
canceling a reservation. A second always-visible icon button works,
but when there isn't room for one, or it would get folded into the
row's own merged description, Compose can expose the action to
TalkBack directly instead:

```kotlin
Row(
    modifier = Modifier.semantics {
        customActions = listOf(
            CustomAccessibilityAction("Add to favorites") {
                onFavorite(site)
                true
            },
        )
    },
) {
    Text(site.name)
}
```

TalkBack reaches a custom action through its own actions menu,
without needing a separate visible element on the row. A touch user
without TalkBack can't reach it the same way, so anything exposed only
as a custom action still needs a real visible control somewhere on
the screen; this is a shortcut for TalkBack users navigating quickly,
not a replacement for a touch target.

### Form fields need labels tied to their input

A `TextField` needs its `label` parameter set, and a validation error needs to be exposed as text TalkBack
reads when it appears, instead of only as a color change on the field border.

## Testing

Any screen built against these rules should be checked with TalkBack
turned on, using a real device or an emulator with TalkBack enabled in
its accessibility settings. Swipe right or
left to move between elements, and confirm each stop reads a label
that describes what it does, in an order that matches the
screen's visual flow.

## Further reading

- [Semantics in Compose](https://developer.android.com/develop/ui/compose/accessibility/semantics):
  what `contentDescription`, `stateDescription`, `liveRegion`, and
  similar properties mean, and how composables set them by default.
- [Merging and clearing semantics](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing):
  how `mergeDescendants` groups a row into one accessibility node,
  and what happens when a merged child is itself interactive.
- [Use the TalkBack menu and reading controls](https://support.google.com/accessibility/android/answer/6007066):
  how a user navigates by heading, list, or other granularity, and
  why marking real structure in semantics matters for that.
- [Use TalkBack gestures](https://support.google.com/accessibility/android/answer/6151827):
  which two- and three-finger gestures TalkBack reserves for its own
  navigation while it's running.
- [Accessibility in Jetpack Compose (codelab)](https://developer.android.com/codelabs/jetpack-compose-accessibility):
  a worked example covering the same properties end to end, including
  how to test them.
- [Accessible touch targets](https://developer.android.com/guide/topics/ui/accessibility/apps):
  the 48dp minimum size and spacing guidance behind the touch target
  rule above.
- [Semantics API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/package-summary):
  the Kotlin API docs for every semantics property mentioned in this
  guide, including the exact merge behavior for each one.

Where this guide and one of these pages disagree, or a page gives a
single documented example rather than a general rule, treat it as a
starting point to confirm with TalkBack on a device, not as settled.
