# Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped write
# this one-off backfill and its emulator test. Human review is pending.
# Purpose: copies each owner's profile name onto their campsites as `ownerName`,
# so the lottery wizard can show "Listed by ..." for campsites saved before
# US 02.04.08 added the copy. Clients can't do this: profiles are private, so
# only the server, which skips the security rules, can read every profile.
#
# Running it (from `functions/`):
#   - Against the Firestore emulator:
#       FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GOOGLE_CLOUD_PROJECT=lambda-campsite \
#       python backfill_owner_names.py
#   - Against the real project (deploy owner only):
#       GOOGLE_CLOUD_PROJECT=<project-id> GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> \
#       python backfill_owner_names.py
#
# Outstanding issues: None known.
"""One-off backfill: every campsite gets its listing owner's profile name."""

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


if __name__ == "__main__":
    # The client honors FIRESTORE_EMULATOR_HOST, or falls back to the
    # service account in GOOGLE_APPLICATION_CREDENTIALS for production.
    client = firestore.Client(project=None)
    print(f"Updated {backfill(client)} campsites.")
