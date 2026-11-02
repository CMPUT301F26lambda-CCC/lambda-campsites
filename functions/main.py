# AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
# this file in October 2026, starting from the file `firebase init` creates.
# It helped with the comments and the Python syntax. Sashreek Addanki reviewed
# it and committed it.
#
# drawLotteries was coded by Aarush Bhat with the assistance of Claude Sonnet 5
# sendPush was coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/architecture.md section 4. Aarush Bhat reviewed and committed it.
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite and cleanUpProfile. Each one
gets added here by its own issue.
"""

import random
from datetime import datetime, timezone

from firebase_admin import firestore, initialize_app
from firebase_functions import scheduler_fn
from firebase_admin import firestore, initialize_app, messaging
from firebase_functions import firestore_fn
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
        _draw_lottery(db, snapshot.reference)


def _draw_lottery(db, lottery_ref):
    _draw_in_transaction(db.transaction(), db, lottery_ref)


@firestore.transactional
def _draw_in_transaction(transaction, db, lottery_ref):
    lottery = lottery_ref.get(transaction=transaction).to_dict()
    # Drawn by an earlier run already: the schedule can fire more than once
    # for the same lottery before a draw finishes.
    if lottery is None or lottery.get("status") != "OPEN":
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
    message = push_for_notification(firestore.client(), event.data.to_dict())
    if message is not None:
        messaging.send(message)


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
