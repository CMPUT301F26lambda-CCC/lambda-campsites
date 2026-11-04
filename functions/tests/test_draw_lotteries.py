# Flow 5: Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI), through Codex, assisted
# with draw metadata/edge-case coverage; human review pending.
# Purpose: tests for the draw_lotteries Cloud Function (US 02.04.06,
# 02.05.01). They call draw_due_lotteries against the Firestore emulator
# with a `now` of their own, so they don't have to wait for the schedule.
#
# Outstanding issues:
# - They don't check that the schedule fires every 5 minutes, because the
#   emulator doesn't run scheduled functions on their schedule.
# - Winners are random, so the tests check how many won, not who.
#
# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed them.
#
# Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
# Sashreek Addanki add the tests for a lottery that can't be drawn and for
# one reopened after the query found it, on November 1, 2026. Sashreek
# Addanki reviewed them.
"""Tests for draw_lotteries, against the Firestore emulator (US 02.05.01).

Run with the Firebase emulators started (`firebase emulators:start`) and,
inside `functions`, `venv/Scripts/pytest` (or `venv/bin/pytest` on Linux
and macOS). See docs/testing.md.
"""

from datetime import datetime, timedelta, timezone

from google.cloud import firestore

import main


def test_draws_the_right_number_of_winners(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=2, registration_closes=now - timedelta(days=1))
    _create_entries(lottery_ref, ["alice", "bob", "carol", "dave"])

    main.draw_due_lotteries(db, now)

    assert lottery_ref.get().get("status") == "DRAWN"
    statuses = _entry_statuses(lottery_ref)
    assert sorted(statuses.values()) == ["NOT_SELECTED", "NOT_SELECTED", "WON", "WON"]
    notifications = _notifications(db)
    assert len(notifications) == 4
    assert sum(1 for n in notifications if n["type"] == "WIN") == 2
    assert sum(1 for n in notifications if n["type"] == "LOSE") == 2


def test_everyone_wins_when_there_are_fewer_entries_than_winners(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=3, registration_closes=now - timedelta(days=1))
    _create_entries(lottery_ref, ["alice", "bob"])

    main.draw_due_lotteries(db, now)

    assert set(_entry_statuses(lottery_ref).values()) == {"WON"}
    assert all(n["type"] == "WIN" for n in _notifications(db))


def test_a_lottery_is_never_drawn_twice(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now - timedelta(days=1))
    _create_entries(lottery_ref, ["alice", "bob"])
    main._draw_lottery(db, lottery_ref, now)
    drawn_once = _entry_statuses(lottery_ref)

    # Calls the per-lottery draw directly, the way an overlapping or retried
    # schedule run would, instead of going through the status == OPEN query.
    main._draw_lottery(db, lottery_ref, now)

    assert _entry_statuses(lottery_ref) == drawn_once
    assert len(_notifications(db)) == 2


def test_a_lottery_whose_registration_hasnt_closed_isnt_drawn(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now + timedelta(days=1))
    _create_entries(lottery_ref, ["alice"])

    main.draw_due_lotteries(db, now)

    assert lottery_ref.get().get("status") == "OPEN"
    assert _notifications(db) == []


def test_empty_draw_saves_zero_counts_and_server_time(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=3, registration_closes=now)
    main.draw_due_lotteries(db, now)
    saved = lottery_ref.get().to_dict()
    assert saved["status"] == "DRAWN"
    assert saved["drawnWinnerCount"] == 0
    assert saved["drawnEntryCount"] == 0
    assert saved["drawnAt"] is not None
    assert _notifications(db) == []


def test_only_registered_waiting_entries_are_drawn_and_withdrawals_stay_excluded(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=5, registration_closes=now)
    _create_entries(lottery_ref, ["eligible", "withdrawn", "already-booked"])
    lottery_ref.collection("entries").document("withdrawn").delete()
    lottery_ref.collection("entries").document("already-booked").update({"status": "BOOKED"})
    main.draw_due_lotteries(db, now)
    assert _entry_statuses(lottery_ref) == {"eligible": "WON", "already-booked": "BOOKED"}
    saved = lottery_ref.get().to_dict()
    assert saved["drawnWinnerCount"] == 1
    assert saved["drawnEntryCount"] == 1
    assert [notice["recipientId"] for notice in _notifications(db)] == ["eligible"]


def test_draw_metadata_is_immutable_on_retry_and_keeps_public_campsite_names(db):
    now = datetime.now(timezone.utc)
    db.collection("campsites").document("campsite-1").set({"name": "Demo Campsite"})
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now)
    _create_entries(lottery_ref, ["alice", "bob"])
    main.draw_due_lotteries(db, now)
    original = lottery_ref.get().to_dict()
    assert original["drawnCampsiteNames"] == ["Demo Campsite"]
    main._draw_lottery(db, lottery_ref, now + timedelta(minutes=5))
    assert lottery_ref.get().to_dict() == original
    assert len(_notifications(db)) == 2


def test_a_lottery_that_cant_be_drawn_doesnt_stop_the_others(db):
    now = datetime.now(timezone.utc)
    # Closes first, so the draw reaches it before the good one.
    broken_ref = _create_lottery(db, number_of_winners=1, registration_closes=now - timedelta(days=2))
    broken_ref.update({"numberOfWinners": firestore.DELETE_FIELD})
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now - timedelta(days=1))
    _create_entries(lottery_ref, ["alice"])

    main.draw_due_lotteries(db, now)

    assert broken_ref.get().get("status") == "OPEN"
    assert lottery_ref.get().get("status") == "DRAWN"


def test_a_lottery_reopened_after_the_query_isnt_drawn(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now - timedelta(days=1))
    _create_entries(lottery_ref, ["alice"])
    # The owner moves the closing time back after the query found the
    # lottery, which the rules allow while it's OPEN.
    lottery_ref.update({"registrationCloses": now + timedelta(days=1)})

    # Calls the per-lottery draw directly, the way the loop would once the
    # query had already returned this lottery.
    main._draw_lottery(db, lottery_ref, now)

    assert lottery_ref.get().get("status") == "OPEN"
    assert _notifications(db) == []


def _create_lottery(db, number_of_winners, registration_closes):
    lottery_ref = db.collection("lotteries").document()
    lottery_ref.set(
        {
            "ownerId": "owner-1",
            "name": "Moose Lake Lottery",
            "campsiteIds": ["campsite-1"],
            "registrationOpens": registration_closes - timedelta(days=7),
            "registrationCloses": registration_closes,
            "stayStart": "2027-07-01",
            "stayEnd": "2027-07-05",
            "numberOfWinners": number_of_winners,
            "status": "OPEN",
            "entryCount": 0,
        }
    )
    return lottery_ref


def _create_entries(lottery_ref, entrant_ids):
    for entrant_id in entrant_ids:
        lottery_ref.collection("entries").document(entrant_id).set(
            {
                "entrantId": entrant_id,
                "joinedAt": datetime.now(timezone.utc),
                "status": "WAITING",
            }
        )


def _entry_statuses(lottery_ref):
    return {entry.id: entry.get("status") for entry in lottery_ref.collection("entries").stream()}


def _notifications(db):
    return [doc.to_dict() for doc in db.collection("notifications").stream()]
