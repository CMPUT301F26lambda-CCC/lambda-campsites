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
red) has no non-visual signal. Pair the color with text or an icon
that states the same thing, like an "Approved" label next to the dot,
so the status is readable by itself.

### Group related content into one node

A campsite list item that renders the name, location, and rating as
three separate `Text` elements forces TalkBack to read them as three
separate stops, out of context from each other. Give the row a single
`contentDescription` summarizing the item ("Elk Island Site 4,
4.5 stars"), or use `Modifier.semantics(mergeDescendants = true)` on
the container so it reads as one unit.

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
read. Use a `Snackbar` or an equivalent text update tied to the
action, not just a silent state change.

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
