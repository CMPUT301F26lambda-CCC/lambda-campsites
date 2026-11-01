# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026, following
# docs/testing.md's Cloud Functions tests section. Aarush Bhat reviewed,
# ran and committed it.
"""Shared pytest fixtures for the Cloud Functions tests.

Points the Firestore client at the emulator instead of the real project,
the way `LambdaTestRunner` does for the Android tests.
"""

import os

os.environ.setdefault("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080")
os.environ.setdefault("GOOGLE_CLOUD_PROJECT", "lambda-campsite")

import pytest
from google.cloud import firestore


@pytest.fixture
def db():
    """A Firestore client pointed at the emulator, with an empty database."""
    client = firestore.Client()
    _clear(client)
    yield client


def _clear(client):
    """Deletes every document, so each test starts with an empty database."""
    for collection in client.collections():
        _delete_collection(collection)


def _delete_collection(collection_ref):
    for doc in collection_ref.stream():
        for subcollection in doc.reference.collections():
            _delete_collection(subcollection)
        doc.reference.delete()
