# Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped write
# these emulator tests for the ownerName backfill. Human review is pending.
"""Verify the one-off ownerName backfill copies profile names onto campsites.

Also verifies that the script only ever reaches the real project when
`--production <project-id>` names it on purpose.
"""
import os

import pytest
from google.cloud import firestore as emulator_firestore

from backfill_owner_names import backfill, client_for


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


def test_refuses_to_guess_the_database(monkeypatch):
    """Without the emulator host or --production, the script stops instead of writing."""
    monkeypatch.delenv("FIRESTORE_EMULATOR_HOST", raising=False)
    with pytest.raises(SystemExit):
        client_for([])


def test_defaults_to_the_emulator_database():
    client = client_for([])
    assert client.project == "lambda-campsite"


def test_production_flag_targets_the_named_project(monkeypatch):
    """--production <project-id> is the only way to reach the real project."""
    captured = {}

    def fake_client(project=None, **_kwargs):
        captured["project"] = project
        return "client"

    monkeypatch.setattr(emulator_firestore, "Client", fake_client)
    assert client_for(["--production", "lambda-campsite-prod"]) == "client"
    assert captured["project"] == "lambda-campsite-prod"


def test_production_flag_requires_a_project_id():
    with pytest.raises(SystemExit):
        client_for(["--production"])
