# Purpose: Sets up shared Firebase emulator fixtures for Cloud Functions tests.
# Outstanding issues: None known.
# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed it.
# Issue #151: GPT-6 (OpenAI), through Codex, assisted me with anonymous
# Admin SDK credentials for callable entry-point tests. Human review pending.
"""Shared pytest fixtures for the Cloud Functions tests.

Points the Firestore client at the emulator instead of the real project,
the way `LambdaTestRunner` does for the Android tests.
"""

import os

os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080")
os.environ.setdefault("GOOGLE_CLOUD_PROJECT", "lambda-campsite")

import pytest
import google.auth
from google.cloud import firestore
from google.auth.credentials import AnonymousCredentials
from google.auth.exceptions import DefaultCredentialsError
import firebase_admin
from firebase_admin import firestore as admin_firestore


@pytest.fixture
def db():
    """A Firestore client pointed at the emulator, with an empty database."""
    client = firestore.Client()
    _clear(client)
    yield client


@pytest.fixture
def admin_db(db, monkeypatch):
    """Use the Firestore emulator through the default Admin SDK app without ADC.

    Importing main creates the production default app. Replace it only for this
    test, then restore the usual default app for the rest of the suite.
    """
    assert os.environ.get("FIRESTORE_EMULATOR_HOST")
    def no_application_default_credentials(*_args, **_kwargs):
        raise DefaultCredentialsError("No application-default credentials in this emulator test")

    monkeypatch.setattr(google.auth, "default", no_application_default_credentials)
    firebase_admin.delete_app(firebase_admin.get_app())
    emulator_app = firebase_admin.initialize_app(
        AnonymousCredentials(), {"projectId": os.environ["GOOGLE_CLOUD_PROJECT"]})
    try:
        yield admin_firestore.client()
    finally:
        firebase_admin.delete_app(emulator_app)
        firebase_admin.initialize_app()


def _clear(client):
    """Deletes every document, so each test starts with an empty database."""
    for collection in client.collections():
        _delete_collection(collection)


def _delete_collection(collection_ref):
    for doc in collection_ref.stream():
        for subcollection in doc.reference.collections():
            _delete_collection(subcollection)
        doc.reference.delete()
