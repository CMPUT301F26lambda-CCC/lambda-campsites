# Code Documentation Guide

> AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
> write this guide in October 2026, following the team's architecture
> and testing docs. It also helped update the controller and screen
> sections when the controllers became ViewModels. The team reviewed and
> edited it in its pull requests.

This document covers how we document code in this app: the AI-use note
every file needs when AI helped write it, KDoc comments in Kotlin,
docstrings in the Python Cloud Functions, and inline comments.
Reviewers check all of these in every pull request.

## AI use in a file

If AI helped write or change any part of a file, the file starts with an
AI-use note, above everything else, including the `package` line. The
note says:

- which model you used, with its name, version and company, like
  "Claude Opus 5.5 (Anthropic)" or "GPT-5 (OpenAI)";
- how you used it, like Claude Code, a chat website, or GitHub Copilot
  in Android Studio;
- what it was used for, by name: which functions or sections it helped
  write, what it suggested, or what it checked;
- what you wrote, changed or checked yourself.

In Kotlin and Python files the note is a comment at the very top. In
Markdown files it's a quote block under the title, like the one at
the top of this guide.

Be specific. "AI was used" doesn't count. "Claude Opus 5.5 (Anthropic),
used through Claude Code, helped write `join` and its transaction; the
error handling and the tests were written by hand" does.

If a later change also used AI, add a line to the existing note
instead of replacing it. A file where nobody used AI doesn't need a
note.

## KDoc basics

KDoc is Kotlin's version of Javadoc. It's a `/** ... */` comment
placed directly above a class, function or property. Android Studio
shows it when you hover over a name, and Dokka can turn it into HTML
pages later.

- Start with one sentence that says what the thing is or does. Add
  more only if the reader needs it.
- Use `@param` for each parameter whose meaning isn't obvious from its
  name, `@return` for what comes back, and `@throws` for each
  exception the caller has to handle.
- On a class, use `@property` for its constructor properties.
- Link to other code with square brackets, like `[Lottery.join]`. The
  link follows the code if it gets renamed.

## What needs KDoc

| What | Needs KDoc | What to write |
|---|---|---|
| Every class, interface and enum | Yes | What it's for, which layer it belongs to, any design choice someone might question, and known problems with their issue number |
| Public and internal functions | Yes | What it does, the rules it enforces, and what happens when it fails |
| Public properties | When the name isn't enough | Units, allowed values, and what `null` means |
| Private functions | Only when the logic isn't obvious | Why it works this way |
| Overrides | Only if the behaviour differs from what it overrides | |
| Test classes | Yes | The stories they cover, as `docs/testing.md` describes |

## What to write in each layer

- **Model classes:** the rules they enforce, with the story ID, like
  "Enforces the waiting-list limit from US 02.04.07."
- **Repositories:** which Firestore or Storage path they use, which
  functions return live data as a `Flow`, whether an operation runs in
  a transaction, and which errors they throw, like offline, permission
  denied or dates taken.
- **Controllers:** for each `onSomething` method, the user action that
  triggers it, the checks it makes, and what `state` shows when it
  fails. On the `XUiState` class, use `@property` to say what each
  value means.
- **Screens:** what the screen shows, which controller it takes, which
  controller methods its buttons call, and the story IDs it covers.
- **Shared components:** what the component shows, what each parameter
  means, and which Figma component it matches.
- **Cloud Functions:** a Python docstring under each function saying
  what triggers it, what it reads and writes, and why it's safe to run
  twice.

## Inline comments

- Explain why, not what. The code already says what it does.
- Don't leave commented-out code. Git keeps the history if we need it
  back.
- Every TODO names an issue, like `TODO(#67)`, so it doesn't get lost.
- Keep comments true. A pull request that changes behaviour updates
  the comments and KDoc in the same pull request.

## In reviews

Before approving a pull request, the reviewer checks that:

- every new class and public function has KDoc;
- every file AI touched starts with an AI-use note that names the model
  and what it was used for;
- comments and KDoc still match what the code does.
