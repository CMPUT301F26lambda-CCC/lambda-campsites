# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed them.
"""Tests for draw_lotteries, against the Firestore emulator (US 02.05.01).

Run with the Firebase emulators started (`firebase emulators:start`) and,
inside `functions`, `venv/Scripts/pytest` (or `venv/bin/pytest` on Linux
and macOS). See docs/testing.md.
"""

from datetime import datetime, timedelta, timezone

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
    main._draw_lottery(db, lottery_ref)
    drawn_once = _entry_statuses(lottery_ref)

    # Calls the per-lottery draw directly, the way an overlapping or retried
    # schedule run would, instead of going through the status == OPEN query.
    main._draw_lottery(db, lottery_ref)

    assert _entry_statuses(lottery_ref) == drawn_once
    assert len(_notifications(db)) == 2


def test_a_lottery_whose_registration_hasnt_closed_isnt_drawn(db):
    now = datetime.now(timezone.utc)
    lottery_ref = _create_lottery(db, number_of_winners=1, registration_closes=now + timedelta(days=1))
    _create_entries(lottery_ref, ["alice"])

    main.draw_due_lotteries(db, now)

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
