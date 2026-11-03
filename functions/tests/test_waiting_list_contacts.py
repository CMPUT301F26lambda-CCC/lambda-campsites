# Feature owner: Lalith Valluru. Purpose: US 02.09.01 contact authorization tests.
# AI use: OpenAI GPT-6 through Codex assisted with emulator security cases.
# Human review pending. Shared db fixture points only at the Firestore emulator.
"""Verify active ownership, registration membership and minimal contact output."""
import pytest
from firebase_functions import https_fn
from main import waiting_list_contacts


def seed(db):
    """Two owners and one private entrant, with extra fields that must not escape."""
    db.document("profiles/owner").set({"roles": ["OWNER"], "ownerBanned": False, "removed": False})
    db.document("lotteries/mine").set({"ownerId": "owner"})
    db.document("lotteries/foreign").set({"ownerId": "someone-else"})
    db.document("lotteries/mine/entries/entrant").set({"entrantId": "entrant"})
    db.document("profiles/entrant").set({"name": "Alex Demo", "email": "alex@example.com",
        "phone": "780-555-0101", "pushToken": "secret", "roles": ["ENTRANT"], "policyVersionAgreed": 5})


def test_returns_only_minimal_contacts_for_members(db):
    seed(db)
    result = waiting_list_contacts(db, "owner", {"lotteryId": "mine", "entrantIds": ["entrant", "nonmember", "entrant"]})
    assert result == {"contacts": [{"entrantId": "entrant", "name": "Alex Demo",
                                   "email": "alex@example.com", "phone": "780-555-0101"}]}


@pytest.mark.parametrize("owner", [{"roles": ["ENTRANT"]}, {"roles": ["OWNER"], "ownerBanned": True},
                                    {"roles": ["OWNER"], "removed": True}])
def test_rejects_inactive_owner(db, owner):
    seed(db)
    db.document("profiles/owner").set(owner)
    with pytest.raises(https_fn.HttpsError) as error:
        waiting_list_contacts(db, "owner", {"lotteryId": "mine", "entrantIds": ["entrant"]})
    assert error.value.code == https_fn.FunctionsErrorCode.PERMISSION_DENIED


def test_rejects_foreign_or_missing_lottery(db):
    seed(db)
    for lottery in ("foreign", "missing"):
        with pytest.raises(https_fn.HttpsError) as error:
            waiting_list_contacts(db, "owner", {"lotteryId": lottery, "entrantIds": ["entrant"]})
        assert error.value.code == https_fn.FunctionsErrorCode.PERMISSION_DENIED


def test_removed_profiles_return_no_personal_data(db):
    seed(db)
    db.document("profiles/entrant").update({"removed": True})
    assert waiting_list_contacts(db, "owner", {"lotteryId": "mine", "entrantIds": ["entrant"]}) == {
        "contacts": [{"entrantId": "entrant", "name": "", "email": None, "phone": None}]}


def test_departed_registration_and_revoked_ownership_cannot_be_read(db):
    seed(db)
    db.document("lotteries/mine/entries/entrant").delete()
    assert waiting_list_contacts(db, "owner", {"lotteryId": "mine", "entrantIds": ["entrant"]}) == {"contacts": []}
    db.document("lotteries/mine").update({"ownerId": "new-owner"})
    with pytest.raises(https_fn.HttpsError):
        waiting_list_contacts(db, "owner", {"lotteryId": "mine", "entrantIds": ["entrant"]})


@pytest.mark.parametrize("data", [None, {}, {"lotteryId": "x/y", "entrantIds": []},
    {"lotteryId": "mine", "entrantIds": ["a/b"]}, {"lotteryId": "mine", "entrantIds": "entrant"},
    {"lotteryId": "mine", "entrantIds": [str(n) for n in range(101)]}])
def test_rejects_invalid_or_oversized_requests(db, data):
    with pytest.raises(https_fn.HttpsError) as error:
        waiting_list_contacts(db, "owner", data)
    assert error.value.code == https_fn.FunctionsErrorCode.INVALID_ARGUMENT
