# Purpose: Exercise waiting-list sends against the local Firestore emulator.
# Outstanding issues: Real FCM delivery requires a deployed device check.
# Lalith Valluru. GPT-6 through Codex assisted with these tests.
from uuid import uuid4

import pytest
from firebase_functions import https_fn

from main import push_for_notification, send_waiting_list_message


def seed(db):
    db.document("profiles/owner").set({"roles": ["OWNER"]})
    db.document("profiles/other").set({"roles": ["OWNER"]})
    lottery = db.document("lotteries/lottery")
    lottery.set({"ownerId": "owner", "name": "Demo lottery"})
    return lottery, {"lotteryId": lottery.id, "message": "Updated dates", "requestId": str(uuid4())}


def test_send_records_current_recipients_and_can_retry(db):
    lottery, request = seed(db)
    for uid, status in [("enabled", "WAITING"), ("disabled", "WAITING"), ("winner", "WON")]:
        lottery.collection("entries").document(uid).set({"status": status})
    db.document("profiles/enabled").set({"notificationsOn": True, "pushToken": "test-enabled"})
    db.document("profiles/disabled").set({"notificationsOn": False, "pushToken": "test-disabled"})

    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 2}
    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 2}
    notices = {doc.get("recipientId"): doc.to_dict() for doc in db.collection("notifications").stream()}
    assert set(notices) == {"enabled", "disabled"}
    assert push_for_notification(db, notices["enabled"]).token == "test-enabled"
    assert push_for_notification(db, notices["disabled"]) is None
    history = db.document(f"profiles/owner/sentNotifications/{request['requestId']}").get().to_dict()
    assert history["recipientIds"] == ["disabled", "enabled"]
    assert history["message"] == "Updated dates"
    assert history["lotteryName"] == "Demo lottery"
    assert history["sentAt"] is not None


def test_foreign_lottery_and_empty_list_do_not_send(db):
    _, request = seed(db)
    for uid, code in [("other", https_fn.FunctionsErrorCode.PERMISSION_DENIED),
                      ("owner", https_fn.FunctionsErrorCode.FAILED_PRECONDITION)]:
        with pytest.raises(https_fn.HttpsError) as failure:
            send_waiting_list_message(db, uid, request)
        assert failure.value.code == code
    assert list(db.collection("notifications").stream()) == []
    assert list(db.collection("profiles/owner/sentNotifications").stream()) == []
