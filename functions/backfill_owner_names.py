# Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped write
# this one-off backfill and its emulator test. Human review is pending.
# Purpose: copies each owner's profile name onto their campsites as `ownerName`,
# so the lottery wizard can show "Listed by ..." for campsites saved before
# US 02.04.08 added the copy. Clients can't do this: profiles are private, so
# only the server, which skips the security rules, can read every profile.
#
# Running it (from `functions/`):
#   - Against the Firestore emulator (the default, and the only target unless
#     you ask for the real project on purpose):
#       FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GOOGLE_CLOUD_PROJECT=lambda-campsite \
#       python backfill_owner_names.py
#   - Against the real project (deploy owner only, deliberate choice):
#       python backfill_owner_names.py --production <project-id>
#     This uses the signed-in Google credentials, like
#     GOOGLE_APPLICATION_CREDENTIALS=<service-account.json>.
#
# Outstanding issues: None known.
"""One-off backfill: every campsite gets its listing owner's profile name."""

import os
import sys

from google.cloud import firestore


def backfill(db):
    """Sets `ownerName` on every campsite from its owner's profile.

    Skips profiles whose name was wiped, and campsites that already carry the
    right name. Commits in batches of 450, under Firestore's 500-write limit.

    Returns how many campsites were updated.
    """
    updated = 0
    batch = db.batch()
    writes = 0
    profiles = db.collection("profiles").stream()
    for profile in profiles:
        data = profile.to_dict() or {}
        name = (data.get("name") or "").strip()
        if not name:
            continue
        campsites = db.collection("campsites").where("ownerId", "==", profile.id).stream()
        for campsite in campsites:
            if (campsite.to_dict() or {}).get("ownerName") == name:
                continue
            batch.update(campsite.reference, {"ownerName": name})
            updated += 1
            writes += 1
            if writes >= 450:
                batch.commit()
                batch = db.batch()
                writes = 0
    if writes:
        batch.commit()
    return updated


def client_for(argv):
    """Builds the Firestore client the run targets.

    The default target is the emulator, and the script refuses to run when
    `FIRESTORE_EMULATOR_HOST` isn't set, so a missing environment variable can
    never point the writes at the real project by accident. Pass
    `--production <project-id>` to run on the real project on purpose.
    """
    if "--production" in argv:
        index = argv.index("--production")
        if index + 1 >= len(argv):
            raise SystemExit("Usage: python backfill_owner_names.py --production <project-id>")
        return firestore.Client(project=argv[index + 1])
    if not os.environ.get("FIRESTORE_EMULATOR_HOST"):
        raise SystemExit(
            "No FIRESTORE_EMULATOR_HOST is set, so there is no emulator to write to. "
            "Set it (see the file header), or pass --production <project-id> to run "
            "on the real project on purpose."
        )
    return firestore.Client(project=os.environ.get("GOOGLE_CLOUD_PROJECT", "lambda-campsite"))


if __name__ == "__main__":
    client = client_for(sys.argv[1:])
    print(f"Updated {backfill(client)} campsites in project '{client.project}'.")
