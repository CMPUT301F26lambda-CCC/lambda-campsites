# Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped write
# these emulator tests for the ownerName backfill. Human review is pending.
"""Verify the one-off ownerName backfill copies profile names onto campsites."""
from backfill_owner_names import backfill


def test_backfill_copies_each_profile_name_onto_their_campsites(db):
    db.document("profiles/owner").set({"name": "Alex Kim"})
    db.collection("campsites").document("unnamed").set({"ownerId": "owner", "name": "Aspen"})
    db.collection("campsites").document("stale").set(
        {"ownerId": "owner", "name": "Birch", "ownerName": "Old Name"})
    db.document("profiles/wiped").set({"name": ""})
    db.collection("campsites").document("wiped-site").set({"ownerId": "wiped", "name": "Cedar"})

    assert backfill(db) == 2

    campsites = {d.id: d.to_dict() for d in db.collection("campsites").stream()}
    assert campsites["unnamed"]["ownerName"] == "Alex Kim"
    assert campsites["stale"]["ownerName"] == "Alex Kim"
    assert campsites["wiped-site"].get("ownerName") is None


def test_backfill_is_safe_to_run_twice(db):
    db.document("profiles/owner").set({"name": "Alex Kim"})
    db.collection("campsites").document("named").set(
        {"ownerId": "owner", "name": "Aspen", "ownerName": "Alex Kim"})

    assert backfill(db) == 0
    assert backfill(db) == 0
