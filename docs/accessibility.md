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

### Selected state needs its own signal, not just a background color

A selected campsite in a browse list that's only shown with a
highlighted background has the same problem as a colored status: no
non-visual signal. Use `Modifier.selectable` (with `selectableGroup()`
on the container) instead of a plain `clickable`, and give the row a
`contentDescription` that states the selection state outright:

```kotlin
LazyColumn(modifier = Modifier.selectableGroup()) {
    items(campsites) { site ->
        val isSelected = site == selectedSite
        Text(
            text = site.name,
            modifier = Modifier
                .selectable(selected = isSelected, onClick = { onSelect(site) })
                .semantics {
                    contentDescription =
                        "${site.name}, ${if (isSelected) "selected" else "not selected"}"
                }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
        )
    }
}
```

`Modifier.selectable` is what makes TalkBack announce "selected" or
"not selected" on its own as a semantic role; the explicit
`contentDescription` on top of that makes sure the row's name and its
state are read together as one phrase, rather than as two separate,
disconnected pieces of information.

### Images need a real description, not a placeholder

Campsite photos uploaded by owners need a `contentDescription` that
says what the image actually is in context ("Photo of Elk Island
Site 4"), not a generic "image" or the filename. If a photo is
decorative only (for example a background texture), mark it
`contentDescription = null` instead of leaving a meaningless label.

### Minimum touch target size

Interactive elements should be at least 48dp in both dimensions, even
if the visible icon or text inside is smaller. Pad a small icon button
out to that size with `Modifier.size(48.dp)` rather than leaving the
tap target the same size as the icon.

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

### Form fields need labels tied to their input

A `TextField` needs its `label` parameter set, not just placeholder
text, and a validation error needs to be exposed as text TalkBack
reads when it appears, not only as a color change on the field border.

## Testing

Any screen built against these rules should be checked with TalkBack
turned on, using a real device or an emulator with TalkBack enabled in
its accessibility settings, not just reviewed visually. Swipe right or
left to move between elements, and confirm each stop reads a label
that actually describes what it does, in an order that matches the
screen's visual flow.
