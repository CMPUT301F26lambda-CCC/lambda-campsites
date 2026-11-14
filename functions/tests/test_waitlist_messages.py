# Purpose: Exercise waiting-list sends against the local Firestore emulator.
# Outstanding issues: Real FCM delivery requires a deployed device check.
# Lalith Valluru. GPT-6 through Codex assisted with these tests.
from uuid import uuid4

import json
import os
from urllib.request import Request, urlopen

import pytest
from flask import Flask, request as flask_request
from firebase_functions import https_fn

from main import push_for_notification, send_waiting_list_message, send_waiting_list_notification
import main


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
    db.document("profiles/enabled").set({"name": "Demo Entrant", "notificationsOn": True, "pushToken": "test-enabled"})
    db.document("profiles/disabled").set({"notificationsOn": False, "pushToken": "test-disabled"})

    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 2}
    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 2}
    notices = {doc.get("recipientId"): doc.to_dict() for doc in db.collection("notifications").stream()}
    assert set(notices) == {"enabled", "disabled"}
    assert push_for_notification(db, notices["enabled"]).token == "test-enabled"
    assert push_for_notification(db, notices["disabled"]) is None
    history = db.document(f"profiles/owner/sentNotifications/{request['requestId']}").get().to_dict()
    assert history["complete"]
    recipients = db.document(f"profiles/owner/sentNotifications/{request['requestId']}").collection("recipients")
    assert {doc.id: doc.get("name") for doc in recipients.stream()} == {
        "disabled": "", "enabled": "Demo Entrant",
    }
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

def test_large_send_resumes_with_the_original_waiting_list(db, monkeypatch):
    lottery, request = seed(db)
    entries = lottery.collection("entries")
    ids = [f"entrant-{i:04}" for i in range(503)]
    for start in range(0, len(ids), 200):
        batch = db.batch()
        for uid in ids[start:start + 200]:
            batch.set(entries.document(uid), {"status": "WAITING"})
        batch.commit()

    send_batch = main._send_waiting_list_batch
    calls = 0

    def interrupt_after_first_batch(*args):
        nonlocal calls
        calls += 1
        if calls == 2:
            raise RuntimeError("Interrupted between batches")
        return send_batch(*args)

    monkeypatch.setattr(main, "_send_waiting_list_batch", interrupt_after_first_batch)
    with pytest.raises(RuntimeError, match="Interrupted"):
        send_waiting_list_message(db, "owner", request)
    history = db.document(f"profiles/owner/sentNotifications/{request['requestId']}")
    assert history.get().get("complete") is False
    assert history.get().get("recipientCount") == 200
    entries.document(ids[-1]).delete()
    entries.document("newcomer").set({"status": "WAITING"})

    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 503}
    assert send_waiting_list_message(db, "owner", request) == {"recipientCount": 503}
    notices = [doc.get("recipientId") for doc in db.collection("notifications").stream()]
    assert sorted(notices) == ids
    assert history.get().get("complete") is True
    assert sorted(doc.id for doc in history.collection("recipients").stream()) == ids


def invoke_callable(data, token=None):
    app = Flask(__name__)
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    with app.test_request_context("/send_waiting_list_notification", method="POST",
                                  json={"data": data}, headers=headers):
        return send_waiting_list_notification(flask_request._get_current_object())


def test_callable_rejects_signed_out_request():
    response = invoke_callable({})
    assert response.status_code == 401
    assert response.get_json()["error"]["status"] == "UNAUTHENTICATED"


def test_callable_accepts_auth_emulator_token(admin_db):
    auth_host = os.environ.get("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099")
    assert auth_host.split(":")[0] in ("localhost", "127.0.0.1")
    endpoint = f"http://{auth_host}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"
    signup = Request(endpoint, json.dumps({"returnSecureToken": True}).encode(),
                     {"Content-Type": "application/json"})
    with urlopen(signup, timeout=5) as response:
        identity = json.load(response)

    lottery = admin_db.document("lotteries/callable-lottery")
    admin_db.document(f"profiles/{identity['localId']}").set({"roles": ["OWNER"]})
    lottery.set({"ownerId": identity["localId"], "name": "Callable lottery"})
    lottery.collection("entries").document("entrant").set({"status": "WAITING"})
    response = invoke_callable({"lotteryId": lottery.id, "message": "Callable check",
                                "requestId": str(uuid4())}, identity["idToken"])
    assert response.status_code == 200
    assert response.get_json() == {"result": {"recipientCount": 1}}
    assert len(list(admin_db.collection("notifications").stream())) == 1
