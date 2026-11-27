# Feature owner: Lalith Valluru. Last updated: 2026-11-03 (Flow 8).
# OpenAI GPT-6 (OpenAI), through Codex, assisted with source lottery IDs in draw notices; human review pending.

# Flow 5: Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI), through Codex, assisted
# with draw metadata/edge-case coverage; human review pending.
# Last updated: 2026-11-03 (Flow 12).
# Purpose: the app's Cloud Functions, the server side of the design in
# docs/architecture.md section 4. They do the work the app can't be
# trusted to do itself, like drawing lotteries and sending pushes. They run
# with the Admin SDK, which skips the security rules, so each one checks
# what it needs on its own.
#
# Outstanding issues:
# - Flow 12: contact callable deployment and shared-project verification remain pending (#129).
# - updateRatingStats isn't written yet. It comes with #111.
# - The clean-up triggers (#113) run once per change. Python Firestore
#   triggers have no retry option, so if one fails partway, whatever is left
#   stays until it's run again by hand. Every step is safe to run twice.
# - "Upcoming" in the clean-up means the stay hasn't ended by today's date
#   in UTC, the same day boundary the rules use for cancelling.
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
# Issue #151: GPT-6 (OpenAI), through Codex, assisted me with the callable's
# Admin SDK client setup; emulator clients stay in test fixtures.
# Issue #35: GPT-6 (OpenAI), through Codex, assisted with the owner-rejection
# notification trigger, transient write retries, and regression tests. Human review is pending.
#
# Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
# Sashreek Addanki write clean_up_campsite, clean_up_profile and
# clean_up_image, and move the notice retry into _create_notice_once so they
# share it, on November 27, 2026 (#113). Sashreek Addanki reviewed them.
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite, cleanUpProfile and
cleanUpImage. Each one gets added here by its own issue.
"""

import random
from uuid import UUID
from hashlib import sha256
from datetime import date, datetime, timedelta, timezone
from time import sleep

from firebase_admin import firestore, initialize_app, messaging, storage
from firebase_functions import firestore_fn, logger, scheduler_fn, https_fn
from firebase_functions.options import set_global_options
from google.api_core.exceptions import (
    Aborted, AlreadyExists, DeadlineExceeded, InternalServerError, NotFound,
    ServiceUnavailable, TooManyRequests,
)
from google.cloud.firestore_v1.base_query import FieldFilter

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
    return waiting_list_contacts(firestore.client(), request.auth.uid, request.data)


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


# Issues #37 and #38: Lalith Valluru. GPT-6 through Codex assisted with
# authenticated sending, deduplication and storing a private history record.
@https_fn.on_call()
def send_waiting_list_notification(request: https_fn.CallableRequest) -> dict:
    """Send one owner's message to the selected lottery's current waiting list."""
    if request.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, "Sign in first.")
    return send_waiting_list_message(firestore.client(), request.auth.uid, request.data)


def send_waiting_list_message(db, uid, data):
    """Validate the message before starting its Firestore transaction."""
    invalid = https_fn.FunctionsErrorCode.INVALID_ARGUMENT
    if not isinstance(data, dict):
        raise https_fn.HttpsError(invalid, "A lottery and message are required.")
    lottery_id, message, request_id = (data.get(key) for key in ("lotteryId", "message", "requestId"))
    if (not isinstance(lottery_id, str) or not lottery_id or "/" in lottery_id
            or lottery_id in (".", "..") or len(lottery_id.encode("utf-8")) > 1500):
        raise https_fn.HttpsError(invalid, "Invalid lottery ID.")
    if not isinstance(message, str) or not 1 <= len(message.strip()) <= 500:
        raise https_fn.HttpsError(invalid, "Enter a message of 1 to 500 characters.")
    try:
        request_id = str(UUID(request_id))
    except (AttributeError, ValueError, TypeError):
        raise https_fn.HttpsError(invalid, "Invalid send request ID.")
    history = db.document(f"profiles/{uid}/sentNotifications/{request_id}")
    lottery = db.document(f"lotteries/{lottery_id}")
    message = message.strip()
    _capture_waiting_list(db.transaction(), db, uid, lottery, history, message)
    while True:
        count = _send_waiting_list_batch(db.transaction(), db, uid, lottery, history, message)
        if count is not None:
            return {"recipientCount": count}


def _owned_waiting_list_lottery(transaction, db, uid, lottery_ref):
    """Check that the caller can still send messages for this lottery."""
    owner = db.document(f"profiles/{uid}").get(transaction=transaction).to_dict() or {}
    lottery = lottery_ref.get(transaction=transaction).to_dict() or {}
    if ("OWNER" not in owner.get("roles", []) or owner.get("ownerBanned", False)
            or owner.get("removed", False) or lottery.get("ownerId") != uid):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.PERMISSION_DENIED, "This lottery is unavailable.")

    return lottery


WAITING_LIST_BATCH_SIZE = 200


@firestore.transactional
def _capture_waiting_list(transaction, db, uid, lottery_ref, history_ref, message):
    """Save the full audience atomically before any recipient is notified."""
    lottery = _owned_waiting_list_lottery(transaction, db, uid, lottery_ref)
    previous = history_ref.get(transaction=transaction).to_dict()
    if previous is not None:
        if previous["lotteryId"] != lottery_ref.id or previous["message"] != message:
            raise https_fn.HttpsError(https_fn.FunctionsErrorCode.ALREADY_EXISTS, "This request has already been used.")
        return

    waiting = (lottery_ref.collection("entries")
               .where(filter=FieldFilter("status", "==", "WAITING")).order_by("__name__"))
    recipient_ids = [entry.id for entry in waiting.stream(transaction=transaction)]
    if not recipient_ids:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION, "No entrants to notify.")
    total = len(recipient_ids)
    for offset in range(0, total, WAITING_LIST_BATCH_SIZE):
        page = history_ref.collection("audience").document(str(offset))
        transaction.create(page, {"ids": recipient_ids[offset:offset + WAITING_LIST_BATCH_SIZE]})
    transaction.create(history_ref, {
        "lotteryId": lottery_ref.id, "lotteryName": lottery.get("name") or lottery_ref.id,
        "message": message, "sentAt": firestore.SERVER_TIMESTAMP,
        "recipientCount": 0, "complete": False, "audienceSize": total,
    })


@firestore.transactional
def _send_waiting_list_batch(transaction, db, uid, lottery_ref, history_ref, message):
    """Commit notifications and advance the saved audience cursor together."""
    _owned_waiting_list_lottery(transaction, db, uid, lottery_ref)
    previous = history_ref.get(transaction=transaction).to_dict()
    if previous is not None:
        if previous["lotteryId"] != lottery_ref.id or previous["message"] != message:
            raise https_fn.HttpsError(https_fn.FunctionsErrorCode.ALREADY_EXISTS, "This request has already been used.")
        if "recipientIds" in previous:
            return len(previous["recipientIds"])
        if previous["complete"]:
            return previous["recipientCount"]

    if previous is None or "audienceSize" not in previous:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
                                 "The original recipient list is unavailable.")
    offset = previous["recipientCount"]
    page = history_ref.collection("audience").document(str(offset))
    selected = page.get(transaction=transaction).get("ids")
    profiles = {doc.id: doc.to_dict() or {} for doc in db.get_all(
        [db.document(f"profiles/{entrant_id}") for entrant_id in selected], transaction=transaction
    )}
    lottery_name = previous["lotteryName"]
    for entrant_id in selected:
        profile = profiles.get(entrant_id, {})
        name = profile.get("name")
        name = name.strip()[:200] if isinstance(name, str) and not profile.get("removed") else ""
        transaction.create(history_ref.collection("recipients").document(entrant_id), {"name": name})
        notification_id = sha256(f"{uid}/{history_ref.id}/{entrant_id}".encode()).hexdigest()
        transaction.create(db.collection("notifications").document(notification_id), {
            "recipientId": entrant_id,
            "type": "WAITING_LIST_MESSAGE",
            "title": lottery_name,
            "body": message,
            "lotteryId": lottery_ref.id,
            "sentAt": firestore.SERVER_TIMESTAMP,
            "read": False,
        })

    count = offset + len(selected)
    complete = count == previous["audienceSize"]
    transaction.update(history_ref, {"recipientCount": count, "complete": complete})
    transaction.delete(page)
    return count if complete else None


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
    # Read public campsite labels before any transaction writes; retain a draw-time snapshot.
    campsite_names = []
    for campsite_id in lottery.get("campsiteIds", []):
        campsite = db.collection("campsites").document(campsite_id).get(transaction=transaction).to_dict() or {}
        if isinstance(campsite.get("name"), str) and campsite["name"].strip():
            campsite_names.append(campsite["name"].strip())
    winners = random.sample(waiting, k=min(lottery["numberOfWinners"], len(waiting)))
    winner_ids = {entry.id for entry in winners}

    for entry in waiting:
        won = entry.id in winner_ids
        transaction.update(entry.reference, {"status": "WON" if won else "NOT_SELECTED"})
        notification = db.collection("notifications").document()
        transaction.set(notification, _lottery_notification(entry.get("entrantId"), lottery["name"], won,
            lottery_ref.id, campsite_names or lottery.get("campsiteIds", []), lottery["registrationOpens"], lottery["registrationCloses"]))

    transaction.update(lottery_ref, {
        "status": "DRAWN", "drawnAt": firestore.SERVER_TIMESTAMP,
        "drawnWinnerCount": len(winner_ids), "drawnEntryCount": len(waiting),
        "drawnCampsiteNames": campsite_names,
    })


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


@firestore_fn.on_document_updated(document="reservations/{reservationId}")
def notify_rejected_reservation(event: firestore_fn.Event) -> None:
    """Notify the entrant once when an owner rejects a confirmed reservation."""
    if event.data is None:
        return
    before = event.data.before.to_dict() if event.data.before else None
    after = event.data.after.to_dict() if event.data.after else None
    create_rejection_notification(firestore.client(), event.params["reservationId"], before, after)


def create_rejection_notification(db, reservation_id, before, after):
    """Create one inbox notice for a CONFIRMED-to-REJECTED change.

    A stable document ID prevents duplicate notices if Firestore delivers the
    event more than once or a write succeeds but its response is lost. The
    Python Firestore trigger has no delivery retry option, so retry transient
    write errors here and raise if all attempts fail.
    """
    if not before or not after or before.get("status") != "CONFIRMED" or after.get("status") != "REJECTED":
        return
    guest_id = after.get("guestId")
    if not guest_id or guest_id != before.get("guestId"):
        return
    campsite = after.get("campsiteName") or "your campsite"
    notification_id = sha256(f"reservation-rejected/{reservation_id}".encode()).hexdigest()
    notice = {
        "recipientId": guest_id,
        "type": "RESERVATION_REJECTED",
        "title": "Reservation rejected",
        "body": f"Your reservation at {campsite} was rejected. Your booked dates have been released.",
        "sentAt": firestore.SERVER_TIMESTAMP,
        "read": False,
    }
    _create_notice_once(db, notification_id, notice)


def _create_notice_once(db, notification_id, notice):
    """Creates one notice under a stable ID, retrying brief write failures.

    The stable ID means a trigger Firestore delivers twice, or a write whose
    response was lost, still leaves one notice. Python Firestore triggers
    have no retry option, so this retries transient errors itself and
    raises if every attempt fails.
    """
    notification_ref = db.collection("notifications").document(notification_id)
    transient_errors = (Aborted, DeadlineExceeded, InternalServerError, ServiceUnavailable, TooManyRequests)
    max_attempts = 3
    for attempt in range(max_attempts):
        try:
            notification_ref.create(notice)
            return
        except AlreadyExists:
            return
        except transient_errors:
            if attempt == max_attempts - 1:
                raise
            sleep(2 ** attempt)


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


# Clean-up (#113). Firestore never deletes a document's subcollections, or
# anything that points at it, so these triggers do it when an admin removes a
# campsite, profile or photo, or someone deletes their own profile
# (US 01.08.01, 03.01.01, 03.02.01, 03.03.01, 03.07.01). Each one is safe to
# run twice.


@firestore_fn.on_document_deleted(document="campsites/{campsiteId}")
def clean_up_campsite(event: firestore_fn.Event) -> None:
    """Runs clean_up_deleted_campsite when a campsite is deleted (US 03.01.01)."""
    if event.data is None:
        return
    clean_up_deleted_campsite(firestore.client(), event.params["campsiteId"],
                              event.data.to_dict() or {}, _today())


def clean_up_deleted_campsite(db, campsite_id, campsite, today):
    """Removes everything that belongs to, or depends on, a deleted campsite.

    - Deletes its subcollections: the availability calendar, and its ratings
      once #111 adds them.
    - Deletes its photo's document, which runs clean_up_image for the file.
    - Takes it out of its OPEN and DRAWN lotteries. A lottery it was the only
      campsite of is ENDED.
    - Cancels its reservations that haven't ended yet.
    - Sends one CAMPSITE_REMOVED notice to its owner, each affected guest and
      each entrant still waiting or holding a win they haven't booked.

    `campsite` is the deleted document's data and `today` is a yyyy-MM-dd
    date. Returns the IDs of everyone it notified.
    """
    name = campsite.get("name") or "A campsite"
    campsite_ref = db.collection("campsites").document(campsite_id)
    for subcollection in campsite_ref.collections():
        db.recursive_delete(subcollection)
    if campsite.get("imageId"):
        db.collection("images").document(campsite["imageId"]).delete()

    # One notice per person. A guest's reservation beats a lottery, which
    # beats being the owner, so each person gets the reason that matters most.
    notices = {}
    owner_id = campsite.get("ownerId")
    if owner_id:
        notices[owner_id] = f"An admin removed your campsite {name}. Its upcoming reservations were cancelled."

    lotteries = db.collection("lotteries").where(
        filter=FieldFilter("campsiteIds", "array_contains", campsite_id)).stream()
    for lottery_snapshot in lotteries:
        lottery = _drop_campsite_from_lottery(db.transaction(), lottery_snapshot.reference, campsite_id)
        if lottery is None:
            continue
        lottery_name = lottery.get("name") or "a lottery"
        if len(lottery.get("campsiteIds", [])) <= 1:
            body = f"{name} was removed, so {lottery_name} has ended."
        else:
            body = f"{name} was removed from {lottery_name}. Its other campsites are still in the lottery."
        for entry in lottery_snapshot.reference.collection("entries").stream():
            if (entry.to_dict() or {}).get("status") in ("WAITING", "WON"):
                notices[entry.id] = body

    reservations = db.collection("reservations").where(
        filter=FieldFilter("campsiteId", "==", campsite_id)).stream()
    for reservation in reservations:
        data = reservation.to_dict() or {}
        if data.get("status") != "CONFIRMED" or data.get("checkOut", "") <= today:
            continue
        reservation.reference.update({"status": "CANCELLED", "cancelledAt": firestore.SERVER_TIMESTAMP})
        if data.get("guestId"):
            notices[data["guestId"]] = (f"{name} was removed by an admin, so your reservation from "
                                        f"{data.get('checkIn')} to {data.get('checkOut')} was cancelled.")

    for recipient_id, body in notices.items():
        _create_notice_once(db, sha256(f"campsite-removed/{campsite_id}/{recipient_id}".encode()).hexdigest(), {
            "recipientId": recipient_id,
            "type": "CAMPSITE_REMOVED",
            "title": "Campsite removed",
            "body": body,
            "sentAt": firestore.SERVER_TIMESTAMP,
            "read": False,
        })
    return sorted(notices)


@firestore.transactional
def _drop_campsite_from_lottery(transaction, lottery_ref, campsite_id):
    """Takes a removed campsite out of an OPEN or DRAWN lottery.

    A lottery left with no campsites is ENDED, since nobody can win or book
    anything in it. Returns the lottery as it was before the change, or None
    if it didn't need one: it already ended, or the campsite is already out.
    """
    lottery = lottery_ref.get(transaction=transaction).to_dict() or {}
    campsite_ids = lottery.get("campsiteIds", [])
    if lottery.get("status") not in ("OPEN", "DRAWN") or campsite_id not in campsite_ids:
        return None
    remaining = [other for other in campsite_ids if other != campsite_id]
    changes = {"campsiteIds": remaining}
    if not remaining:
        changes["status"] = "ENDED"
    transaction.update(lottery_ref, changes)
    return lottery


@firestore_fn.on_document_written(document="profiles/{uid}")
def clean_up_profile(event: firestore_fn.Event) -> None:
    """Runs clean_up_profile_data when a profile is deleted (US 01.08.01), or an
    admin removes it (US 03.02.01) or bans its owner (US 03.07.01)."""
    if event.data is None:
        return
    before = _snapshot_data(event.data.before)
    after = _snapshot_data(event.data.after)
    reason = profile_cleanup_reason(before, after)
    if reason is not None:
        clean_up_profile_data(firestore.client(), event.params["uid"], before, _today(), reason)


def _snapshot_data(snapshot):
    """A snapshot's fields, or None if it's missing or the document doesn't exist."""
    return snapshot.to_dict() if snapshot is not None and snapshot.exists else None


def profile_cleanup_reason(before, after):
    """Why a write to a profile needs clean-up: "deleted", "removed", "banned" or None.

    Only the change into one of those states counts, so later writes to an
    already removed or banned profile, like clearing its details, don't run
    the clean-up again.
    """
    if before is None:
        return None
    if after is None:
        return "deleted"
    if after.get("removed", False) and not before.get("removed", False):
        return "removed"
    if after.get("ownerBanned", False) and not before.get("ownerBanned", False):
        return "banned"
    return None


def clean_up_profile_data(db, uid, profile, today, reason):
    """Removes what a deleted, removed or banned profile leaves behind.

    For a deleted or removed profile:
    - takes the person off every OPEN lottery's waiting list, and declines
      any win they haven't booked;
    - cancels their reservations that haven't ended and frees those nights;
    - deletes their photo's document, which runs clean_up_image.
    For all three, it deletes their campsites, which runs clean_up_campsite
    for each (US 03.07.01). A removed profile also loses its name, email,
    phone, push token and photo, so only its roles and `removed` are left,
    and the app can't recreate it.

    `profile` is the profile's data before the change, `today` a yyyy-MM-dd
    date and `reason` what profile_cleanup_reason returned.
    """
    if reason in ("deleted", "removed"):
        entries = db.collection_group("entries").where(filter=FieldFilter("entrantId", "==", uid)).stream()
        for entry in entries:
            _leave_lottery(db.transaction(), entry.reference.parent.parent, entry.reference)

        reservations = db.collection("reservations").where(filter=FieldFilter("guestId", "==", uid)).stream()
        for reservation in reservations:
            data = reservation.to_dict() or {}
            if data.get("status") != "CONFIRMED" or data.get("checkOut", "") <= today:
                continue
            batch = db.batch()
            batch.update(reservation.reference, {"status": "CANCELLED", "cancelledAt": firestore.SERVER_TIMESTAMP})
            nights = _nights(data.get("checkIn"), data.get("checkOut"))
            calendar_ref = db.document(f"campsites/{data.get('campsiteId')}/availability/calendar")
            if nights and calendar_ref.get().exists:
                batch.update(calendar_ref, {"bookedNights": firestore.ArrayRemove(nights)})
            batch.commit()

        if profile.get("imageId"):
            db.collection("images").document(profile["imageId"]).delete()

    for campsite in db.collection("campsites").where(filter=FieldFilter("ownerId", "==", uid)).stream():
        campsite.reference.delete()

    if reason == "removed":
        try:
            db.collection("profiles").document(uid).update({
                "name": "", "email": "", "phone": None, "pushToken": None, "imageId": None,
            })
        except NotFound:
            pass  # Deleted since it was removed, so there's nothing left to clear.


@firestore.transactional
def _leave_lottery(transaction, lottery_ref, entry_ref):
    """Takes a deleted or removed entrant out of one lottery.

    While the lottery is OPEN, the entry is deleted and the waiting list
    shrinks by one, like leaving in the app does. After the draw, a win they
    haven't booked is DECLINED, so nobody books in their name.
    """
    lottery = lottery_ref.get(transaction=transaction).to_dict() or {}
    entry = entry_ref.get(transaction=transaction).to_dict()
    if entry is None:
        return
    if lottery.get("status") == "OPEN" and entry.get("status", "WAITING") == "WAITING":
        transaction.delete(entry_ref)
        transaction.update(lottery_ref, {"entryCount": max(0, int(lottery.get("entryCount", 0)) - 1)})
    elif entry.get("status") == "WON":
        transaction.update(entry_ref, {"status": "DECLINED"})


def _nights(check_in, check_out):
    """The yyyy-MM-dd nights from check-in up to, but not including, check-out."""
    try:
        start, end = date.fromisoformat(check_in), date.fromisoformat(check_out)
    except (TypeError, ValueError):
        return []
    return [(start + timedelta(days=offset)).isoformat() for offset in range((end - start).days)]


def _today():
    """Today's date as yyyy-MM-dd in UTC, the day boundary the rules use too."""
    return datetime.now(timezone.utc).date().isoformat()


@firestore_fn.on_document_deleted(document="images/{imageId}")
def clean_up_image(event: firestore_fn.Event) -> None:
    """Runs clean_up_deleted_image when a photo's document is deleted (US 03.03.01)."""
    clean_up_deleted_image(firestore.client(), event.params["imageId"], _delete_photo_file)


def clean_up_deleted_image(db, image_id, delete_file):
    """Deletes a removed photo's file and clears it from whatever used it.

    The campsite or profile then shows its placeholder (US 03.03.01).
    `delete_file` deletes images/{image_id}.jpg from Storage. The tests pass
    a fake, since the functions tests don't run the Storage emulator.
    """
    delete_file(image_id)
    for collection in ("campsites", "profiles"):
        for document in db.collection(collection).where(filter=FieldFilter("imageId", "==", image_id)).stream():
            document.reference.update({"imageId": None})


def _delete_photo_file(image_id):
    """Deletes images/{image_id}.jpg from the default Storage bucket, if it's there."""
    try:
        storage.bucket().blob(f"images/{image_id}.jpg").delete()
    except NotFound:
        pass
