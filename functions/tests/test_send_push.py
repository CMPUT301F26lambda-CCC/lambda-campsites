# Purpose: tests for the send_push Cloud Function (US 01.10.01), against
# the Firestore emulator: what it decides to send, and when it clears a
# token FCM says is stale.
#
# Outstanding issues:
# - FCM itself isn't tested. The emulators don't cover Cloud Messaging,
#   so the stale-token tests replace messaging.send with a stand-in.
#
# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed them.
#
# Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
# Sashreek Addanki add the deliver_push tests for keeping and clearing a
# push token, on November 1, 2026. Sashreek Addanki reviewed them.
"""Tests for push_for_notification and deliver_push, against the Firestore
emulator (US 01.10.01).

They check what send_push decides to send, not that FCM delivers it: the
Firebase emulators don't cover Cloud Messaging, so there's nothing local
to send a real push to. The deliver_push tests replace messaging.send
instead. See docs/testing.md.

Run with the Firebase emulators started (`firebase emulators:start`) and,
inside `functions`, `venv/Scripts/pytest` (or `venv/bin/pytest` on Linux
and macOS).
"""

from firebase_admin import messaging

import main


def test_sends_a_push_when_notifications_are_on(db):
    _create_profile(db, "alice", push_token="token-1", notifications_on=True)
    notification = _notification(recipient_id="alice")

    message = main.push_for_notification(db, notification)

    assert message.token == "token-1"
    assert message.notification.title == notification["title"]
    assert message.notification.body == notification["body"]


def test_skips_when_notifications_are_off(db):
    _create_profile(db, "alice", push_token="token-1", notifications_on=False)

    message = main.push_for_notification(db, _notification(recipient_id="alice"))

    assert message is None


def test_skips_when_there_is_no_push_token(db):
    _create_profile(db, "alice", push_token=None, notifications_on=True)

    message = main.push_for_notification(db, _notification(recipient_id="alice"))

    assert message is None


def test_skips_when_the_recipient_has_no_profile(db):
    message = main.push_for_notification(db, _notification(recipient_id="no-such-profile"))

    assert message is None


def test_a_working_token_is_kept(db, monkeypatch):
    _create_profile(db, "alice", push_token="token-1", notifications_on=True)
    monkeypatch.setattr(main.messaging, "send", lambda message: "message-id")

    main.deliver_push(db, _notification(recipient_id="alice"))

    assert _push_token(db, "alice") == "token-1"


def test_a_token_fcm_says_is_unregistered_is_cleared(db, monkeypatch):
    _create_profile(db, "alice", push_token="token-1", notifications_on=True)
    monkeypatch.setattr(main.messaging, "send", _fcm_says_unregistered)

    main.deliver_push(db, _notification(recipient_id="alice"))

    assert _push_token(db, "alice") is None


def test_a_newer_token_saved_meanwhile_is_kept(db, monkeypatch):
    _create_profile(db, "alice", push_token="token-1", notifications_on=True)

    def phone_saves_a_new_token_while_fcm_answers(message):
        db.collection("profiles").document("alice").update({"pushToken": "token-2"})
        _fcm_says_unregistered(message)

    monkeypatch.setattr(main.messaging, "send", phone_saves_a_new_token_while_fcm_answers)

    main.deliver_push(db, _notification(recipient_id="alice"))

    assert _push_token(db, "alice") == "token-2"


def _create_profile(db, uid, push_token, notifications_on):
    db.collection("profiles").document(uid).set(
        {
            "name": "Alex Kim",
            "email": "alex@example.com",
            "notificationsOn": notifications_on,
            "pushToken": push_token,
        }
    )


def _notification(recipient_id):
    return {
        "recipientId": recipient_id,
        "type": "WIN",
        "title": "You won the lottery",
        "body": "You won the lottery for Moose Lake Campsite.",
        "read": False,
    }


def _fcm_says_unregistered(message):
    """Stands in for messaging.send when FCM rejects the token."""
    raise messaging.UnregisteredError("Requested entity was not found.")


def _push_token(db, uid):
    return db.collection("profiles").document(uid).get().get("pushToken")
