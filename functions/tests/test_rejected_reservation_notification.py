# Purpose: checks the entrant notification created after owner rejection.
# Outstanding issues: FCM delivery itself needs a device check.
# AI use: GPT-6 (OpenAI), through Codex, assisted with US 02.08.01
# notification regression tests. Human review is pending.
"""Run against the Firestore emulator, like the other Cloud Functions tests."""

import main
from types import SimpleNamespace


def reservation(status="CONFIRMED"):
    return {
        "status": status,
        "guestId": "entrant-1",
        "ownerId": "owner-1",
        "campsiteName": "Aspen Hollow",
        "checkIn": "2027-07-06",
        "checkOut": "2027-07-08",
    }


def test_rejection_notifies_entrant_once(db):
    main.create_rejection_notification(db, "reservation-1", reservation(), reservation("REJECTED"))
    main.create_rejection_notification(db, "reservation-1", reservation(), reservation("REJECTED"))

    notices = list(db.collection("notifications").stream())
    assert len(notices) == 1
    notice = notices[0].to_dict()
    assert notice["recipientId"] == "entrant-1"
    assert notice["type"] == "RESERVATION_REJECTED"
    assert "Aspen Hollow" in notice["body"]
    assert notice["read"] is False
    assert notice["sentAt"] is not None


def test_unrelated_changes_do_not_notify(db):
    main.create_rejection_notification(db, "reservation-1", reservation(), reservation())
    main.create_rejection_notification(db, "reservation-1", reservation("CANCELLED"), reservation("REJECTED"))
    main.create_rejection_notification(db, "reservation-1", reservation(),
                                       {**reservation("REJECTED"), "guestId": "different-entrant"})

    assert list(db.collection("notifications").stream()) == []


def test_trigger_entry_point_uses_updated_reservation(admin_db):
    """Exercise the event handler with the emulator Admin SDK client."""
    before = SimpleNamespace(to_dict=lambda: reservation())
    after = SimpleNamespace(to_dict=lambda: reservation("REJECTED"))
    event = SimpleNamespace(data=SimpleNamespace(before=before, after=after),
                            params={"reservationId": "reservation-1"})

    main.notify_rejected_reservation.__wrapped__(event)

    notice = next(admin_db.collection("notifications").stream()).to_dict()
    assert notice["recipientId"] == "entrant-1"
    assert notice["type"] == "RESERVATION_REJECTED"
