# Feature owner: Lalith Valluru. Last updated: 2026-11-03 (Flow 8).
# OpenAI GPT-6 (OpenAI), through Codex, assisted with source lottery IDs in draw notices; human review pending.
# Purpose: the app's Cloud Functions, the server side of the design in
# docs/architecture.md section 4. They do the work the app can't be
# trusted to do itself, like drawing lotteries and sending pushes. They run
# with the Admin SDK, which skips the security rules, so each one checks
# what it needs on its own.
#
# Outstanding issues:
# - Flow 12: contact callable deployment and shared-project verification remain pending (#129).
# - updateRatingStats, cleanUpCampsite and cleanUpProfile aren't written
#   yet. Each one comes with its own issue.
# - send_push only clears a token when FCM says it's unregistered. Any
#   other send error, like FCM being down, fails the function and that
#   push is lost. Retrying could send the same push twice, and the in-app
#   notification is there either way.
# - send_push only runs for notifications. Messages get a trigger with #112.
# - A lottery the draw can't finish is logged and left OPEN instead of
#   being marked failed. That way nothing about it changes until someone
#   reads the log and fixes its document, and the next run tries again.
#
# AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
# this file in October 2026, starting from the file `firebase init` creates.
# It helped with the comments and the Python syntax. Sashreek Addanki reviewed
# it and committed it.
#
# drawLotteries and sendPush were coded by Aarush Bhat with the assistance of
# Claude Sonnet 5 (Anthropic), through Claude Code, in November 2026,
# following docs/architecture.md section 4. Aarush Bhat reviewed and
# committed them.
#
# Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
# Sashreek Addanki make the draw skip a lottery it can't draw instead of
# stopping, recheck inside the draw transaction that registration has
# closed, tidy the imports and spacing a merge left doubled, and clear a
# push token FCM says is stale, on November 1, 2026. Sashreek Addanki
# reviewed them.
# Flow 12 feature owner: Lalith Valluru. OpenAI GPT-6 (OpenAI), through Codex,
# assisted with owner_waiting_list_contacts, its contact projection and validation.
# Human review pending.
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite and cleanUpProfile. Each one
gets added here by its own issue.
"""

import random
import os
from datetime import datetime, timezone

from firebase_admin import firestore, initialize_app, messaging
from firebase_functions import firestore_fn, logger, scheduler_fn, https_fn
from firebase_functions.options import set_global_options
from google.cloud.firestore_v1.base_query import FieldFilter
from google.cloud import firestore as cloud_firestore

# At most 10 copies of each function run at once, so a bug or a burst of
# requests can't start hundreds of them and run up a bill.
set_global_options(max_instances=10)

initialize_app()


# Flow 12 feature owner: Lalith Valluru. OpenAI GPT-6 through Codex assisted
# with authenticated contact projection and validation. Human review pending.
@https_fn.on_call()
def owner_waiting_list_contacts(request: https_fn.CallableRequest) -> dict:
    """US 02.09.01: return only contacts for an active owner's lottery entries."""
    if request.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Sign in first.")
    # A local test suite has no production service-account credentials. The
    # Google client uses anonymous credentials only when an emulator host is set.
    # Production always uses the Admin SDK's deployed service identity.
    db = (cloud_firestore.Client(project=os.environ.get("GCLOUD_PROJECT"))
          if os.environ.get("FUNCTIONS_EMULATOR") == "true" and os.environ.get("FIRESTORE_EMULATOR_HOST")
          else firestore.client())
    return waiting_list_contacts(db, request.auth.uid, request.data)


def waiting_list_contacts(db, uid, data):
    """Validate ownership and membership before exposing a minimal contact projection.

    Direct profile reads remain private under firestore.rules. IDs are bounded to
    100 per request; the app batches larger lists. No contact data is logged.
    """
    def valid_id(value):
        return isinstance(value, str) and 0 < len(value) <= 1500 and "/" not in value and value not in (".", "..")

    if not isinstance(data, dict) or not valid_id(data.get("lotteryId")):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "A lottery ID is required.")
    ids = data.get("entrantIds")
    if not isinstance(ids, list) or len(ids) > 100 or not all(valid_id(value) for value in ids):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "Request up to 100 entrant IDs.")
    return _waiting_list_contacts_transaction(db.transaction(), db, uid, data["lotteryId"], list(dict.fromkeys(ids)))


@firestore.transactional
def _waiting_list_contacts_transaction(transaction, db, uid, lottery_id, ids):
    """Read authorization and projections together; retries recheck ownership/membership."""
    owner = db.document(f"profiles/{uid}").get(transaction=transaction).to_dict() or {}
    lottery_ref = db.document(f"lotteries/{lottery_id}")
    lottery = lottery_ref.get(transaction=transaction).to_dict() or {}
    if ("OWNER" not in owner.get("roles", []) or owner.get("ownerBanned", False)
            or owner.get("removed", False) or lottery.get("ownerId") != uid):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.PERMISSION_DENIED, "This waiting list is not available to you.")
    result = []
    for entrant_id in ids:
        entry = lottery_ref.collection("entries").document(entrant_id).get(transaction=transaction)
        if not entry.exists:
            continue
        profile = db.document(f"profiles/{entrant_id}").get(transaction=transaction).to_dict() or {}
        if profile.get("removed", False):
            profile = {}
        def text(field):
            value = profile.get(field)
            return value.strip() if isinstance(value, str) and value.strip() else None
        result.append({"entrantId": entrant_id, "name": text("name") or "",
                       "email": text("email"), "phone": text("phone")})
    return {"contacts": result}


@scheduler_fn.on_schedule(schedule="every 5 minutes")
def draw_lotteries(event: scheduler_fn.ScheduledEvent) -> None:
    """Draws every lottery whose registration has closed (US 02.04.06, 02.05.01)."""
    draw_due_lotteries(firestore.client(), datetime.now(timezone.utc))


def draw_due_lotteries(db, now):
    """The query and loop `draw_lotteries` runs on its schedule.

    Split out so tests can call it directly, with a `now` of their own,
    instead of waiting for the schedule to fire.
    """
    due = (
        db.collection("lotteries")
        .where(filter=FieldFilter("status", "==", "OPEN"))
        .where(filter=FieldFilter("registrationCloses", "<=", now))
    )
    for snapshot in due.stream():
        # One lottery that can't be drawn mustn't stop the rest. Results come
        # in registrationCloses order, so the same broken lottery would come
        # first on every run and block every lottery that closes after it.
        # It stays OPEN, so the log says which one to look at.
        try:
            _draw_lottery(db, snapshot.reference, now)
        except Exception as error:
            logger.error(f"Couldn't draw lottery {snapshot.id}: {error!r}")


def _draw_lottery(db, lottery_ref, now):
    _draw_in_transaction(db.transaction(), db, lottery_ref, now)


@firestore.transactional
def _draw_in_transaction(transaction, db, lottery_ref, now):
    lottery = lottery_ref.get(transaction=transaction).to_dict()
    # Drawn by an earlier run already: the schedule can fire more than once
    # for the same lottery before a draw finishes.
    if lottery is None or lottery.get("status") != "OPEN":
        return
    # The owner can still move registrationCloses while the lottery is OPEN,
    # so registration may have reopened since the query found it.
    if lottery["registrationCloses"] > now:
        return

    waiting = (
        lottery_ref.collection("entries")
        .where(filter=FieldFilter("status", "==", "WAITING"))
        .get(transaction=transaction)
    )
    campsite_names = []
    for campsite_id in lottery.get("campsiteIds", []):
        campsite = db.collection("campsites").document(campsite_id).get(transaction=transaction).to_dict() or {}
        campsite_names.append(campsite.get("name") or campsite_id)
    winners = random.sample(waiting, k=min(lottery["numberOfWinners"], len(waiting)))
    winner_ids = {entry.id for entry in winners}

    for entry in waiting:
        won = entry.id in winner_ids
        transaction.update(entry.reference, {"status": "WON" if won else "NOT_SELECTED"})
        notification = db.collection("notifications").document()
        transaction.set(notification, _lottery_notification(entry.get("entrantId"), lottery["name"], won,
            lottery_ref.id, campsite_names, lottery["registrationOpens"], lottery["registrationCloses"]))

    transaction.update(lottery_ref, {"status": "DRAWN"})


def _lottery_notification(recipient_id, lottery_name, won, lottery_id, campsite_names, registration_opens, registration_closes):
    """A WIN or LOSE notice with a stable source lottery ID for booking handoff."""
    if won:
        title = "You won the lottery"
        body = f"You won the lottery for {lottery_name}."
        notification_type = "WIN"
    else:
        title = "You weren't selected"
        body = f"You weren't selected in the lottery for {lottery_name}."
        notification_type = "LOSE"
    # Keep public campsite labels and the registration period readable even after a lottery is removed.
    body += f" Campsites: {', '.join(campsite_names)}. Registration: {registration_opens.isoformat()} to {registration_closes.isoformat()}."
    return {
        "recipientId": recipient_id,
        "lotteryId": lottery_id,
        "type": notification_type,
        "title": title,
        "body": body,
        "sentAt": firestore.SERVER_TIMESTAMP,
        "read": False,
    }


@firestore_fn.on_document_created(document="notifications/{notificationId}")
def send_push(event: firestore_fn.Event) -> None:
    """Sends a push for a new notification, through FCM (US 01.10.01).

    Only notifications trigger this so far. A trigger for new messages
    arrives with #112, once that story defines how a message is stored.
    """
    if event.data is None:
        return
    deliver_push(firestore.client(), event.data.to_dict())


def deliver_push(db, notification):
    """Sends the push for `notification`, if there is one to send.

    If FCM says the recipient's token isn't registered anymore, because the
    app was uninstalled or its data cleared, the token is cleared from the
    profile, so later notifications skip the push instead of failing the
    same way. Split out from send_push so tests can run it with FCM's send
    replaced.
    """
    message = push_for_notification(db, notification)
    if message is None:
        return
    try:
        messaging.send(message)
    except messaging.UnregisteredError:
        profile_ref = db.collection("profiles").document(notification["recipientId"])
        _clear_stale_token(db.transaction(), profile_ref, message.token)


@firestore.transactional
def _clear_stale_token(transaction, profile_ref, stale_token):
    profile = profile_ref.get(transaction=transaction).to_dict()
    # The phone may have saved a new token since the push was built. Only
    # the token FCM rejected gets cleared.
    if profile is not None and profile.get("pushToken") == stale_token:
        transaction.update(profile_ref, {"pushToken": None})


def push_for_notification(db, notification):
    """The FCM message to send for `notification`, or `None` to skip it.

    Split out so tests can check what would be sent without calling FCM,
    which the Firebase emulators don't cover.
    """
    profile = db.collection("profiles").document(notification["recipientId"]).get().to_dict()
    if profile is None or not profile.get("notificationsOn", True) or not profile.get("pushToken"):
        return None
    # Message.token is deprecated in favour of Message.fid, the Firebase
    # Installation ID. The Android side still saves an FCM token, since
    # FirebaseMessagingService's own token callback has the same move
    # pending with no FID-based replacement shipped yet, so both sides stay
    # on the token they actually share.
    return messaging.Message(
        token=profile["pushToken"],
        notification=messaging.Notification(title=notification["title"], body=notification["body"]),
    )
