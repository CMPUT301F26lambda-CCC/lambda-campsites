# Purpose: checks the entrant notification created after owner rejection.
# Outstanding issues: FCM delivery itself needs a device check.
# AI use: GPT-6 (OpenAI), through Codex, assisted with US 02.08.01
# notification and transient retry regression tests. Human review is pending.
"""Run against the Firestore emulator, like the other Cloud Functions tests."""

from types import SimpleNamespace

from google.api_core.exceptions import ServiceUnavailable
import pytest

import main


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


def test_transient_write_failure_retries_without_duplicate_notice(admin_db, monkeypatch):
    notification_ref = admin_db.collection("notifications").document("example")
    reference_class = type(notification_ref)
    real_create = reference_class.create
    attempts = 0

    def fail_first_attempt(self, data, **kwargs):
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise ServiceUnavailable("Temporary Firestore outage")
        return real_create(self, data, **kwargs)

    monkeypatch.setattr(reference_class, "create", fail_first_attempt)
    monkeypatch.setattr(main, "sleep", lambda _seconds: None)

    before = SimpleNamespace(to_dict=lambda: reservation())
    after = SimpleNamespace(to_dict=lambda: reservation("REJECTED"))
    event = SimpleNamespace(data=SimpleNamespace(before=before, after=after),
                            params={"reservationId": "reservation-1"})
    main.notify_rejected_reservation.__wrapped__(event)
    main.notify_rejected_reservation.__wrapped__(event)

    notices = list(admin_db.collection("notifications").stream())
    assert attempts == 3
    assert len(notices) == 1
    assert notices[0].to_dict()["recipientId"] == "entrant-1"


def test_exhausted_retries_still_report_failure(admin_db, monkeypatch):
    notification_ref = admin_db.collection("notifications").document("example")
    attempts = 0

    def unavailable(_self, _data):
        nonlocal attempts
        attempts += 1
        raise ServiceUnavailable("Offline")

    monkeypatch.setattr(type(notification_ref), "create", unavailable)
    monkeypatch.setattr(main, "sleep", lambda _seconds: None)

    with pytest.raises(ServiceUnavailable):
        main.create_rejection_notification(admin_db, "reservation-1", reservation(), reservation("REJECTED"))

    assert attempts == 3
    assert list(admin_db.collection("notifications").stream()) == []
