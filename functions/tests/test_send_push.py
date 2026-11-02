# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed them.
"""Tests for push_for_notification, against the Firestore emulator
(US 01.10.01).

They check what send_push decides to send, not that FCM delivers it: the
Firebase emulators don't cover Cloud Messaging, so there's nothing local
to send a real push to. See docs/testing.md.

Run with the Firebase emulators started (`firebase emulators:start`) and,
inside `functions`, `venv/Scripts/pytest` (or `venv/bin/pytest` on Linux
and macOS).
"""

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
