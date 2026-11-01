# AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
# this file in October 2026, starting from the file `firebase init` creates.
# It helped with the comments and the Python syntax. Sashreek Addanki reviewed
# it and committed it.
#
# sendPush was coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/architecture.md section 4. Aarush Bhat reviewed and committed it.
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite and cleanUpProfile. Each one
gets added here by its own issue.
"""

from firebase_admin import firestore, initialize_app, messaging
from firebase_functions import firestore_fn
from firebase_functions.options import set_global_options

# At most 10 copies of each function run at once, so a bug or a burst of
# requests can't start hundreds of them and run up a bill.
set_global_options(max_instances=10)

initialize_app()


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
