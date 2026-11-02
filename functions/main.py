# Purpose: the app's Cloud Functions, the server side of the design in
# docs/architecture.md section 4. They do the work the app can't be
# trusted to do itself, like drawing lotteries and sending pushes. They run
# with the Admin SDK, which skips the security rules, so each one checks
# what it needs on its own.
#
# Outstanding issues:
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
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite and cleanUpProfile. Each one
gets added here by its own issue.
"""

import random
from datetime import datetime, timezone

from firebase_admin import firestore, initialize_app, messaging
from firebase_functions import firestore_fn, logger, scheduler_fn
from firebase_functions.options import set_global_options
from google.cloud.firestore_v1.base_query import FieldFilter

# At most 10 copies of each function run at once, so a bug or a burst of
# requests can't start hundreds of them and run up a bill.
set_global_options(max_instances=10)

initialize_app()


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
    winners = random.sample(waiting, k=min(lottery["numberOfWinners"], len(waiting)))
    winner_ids = {entry.id for entry in winners}

    for entry in waiting:
        won = entry.id in winner_ids
        transaction.update(entry.reference, {"status": "WON" if won else "NOT_SELECTED"})
        notification = db.collection("notifications").document()
        transaction.set(notification, _lottery_notification(entry.get("entrantId"), lottery["name"], won))

    transaction.update(lottery_ref, {"status": "DRAWN"})


def _lottery_notification(recipient_id, lottery_name, won):
    """A WIN or LOSE notification document, the way NotificationFields.kt stores one."""
    if won:
        title = "You won the lottery"
        body = f"You won the lottery for {lottery_name}."
        notification_type = "WIN"
    else:
        title = "You weren't selected"
        body = f"You weren't selected in the lottery for {lottery_name}."
        notification_type = "LOSE"
    return {
        "recipientId": recipient_id,
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
