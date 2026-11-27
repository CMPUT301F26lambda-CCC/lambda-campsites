# Purpose: checks the clean-up Cloud Functions (#113) against the Firestore
# emulator: what clean_up_campsite, clean_up_profile and clean_up_image delete,
# cancel, change and notify when a campsite, profile or photo goes away
# (US 01.08.01, 03.01.01, 03.02.01, 03.03.01, 03.07.01).
#
# Outstanding issues:
# - Deleting the photo file itself isn't tested here, since these tests don't
#   run the Storage emulator. clean_up_deleted_image takes the delete as a
#   parameter, and the test checks it's called with the right ID.
#
# AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
# these tests on November 27, 2026, following the clean-up design in
# docs/architecture.md section 4. It helped with the test cases and the
# Python syntax. Sashreek Addanki reviewed it.
"""Run against the Firestore emulator, like the other Cloud Functions tests."""

from types import SimpleNamespace

import main

TODAY = "2026-11-27"


def add_campsite(db, campsite_id="pine", owner="owner-1", name="Pine Ridge", image_id=None):
    data = {"ownerId": owner, "name": name, "location": "Banff, AB", "maxOccupancy": 4,
            "published": True, "imageId": image_id}
    db.document(f"campsites/{campsite_id}").set(data)
    db.document(f"campsites/{campsite_id}/availability/calendar").set(
        {"seasonStart": "2026-11-01", "seasonEnd": "2027-10-31", "bookedNights": []})
    return data


def add_lottery(db, lottery_id, campsite_ids, status="OPEN", entries=None, name="Summer lottery"):
    entries = entries or {}
    db.document(f"lotteries/{lottery_id}").set({
        "ownerId": "owner-1", "name": name, "campsiteIds": campsite_ids, "status": status,
        "entryCount": len(entries), "numberOfWinners": 2,
    })
    for entrant_id, entry_status in entries.items():
        db.document(f"lotteries/{lottery_id}/entries/{entrant_id}").set(
            {"entrantId": entrant_id, "status": entry_status})


def add_reservation(db, reservation_id, check_in, check_out, status="CONFIRMED",
                    campsite_id="pine", guest="guest-1"):
    db.document(f"reservations/{reservation_id}").set({
        "guestId": guest, "ownerId": "owner-1", "campsiteId": campsite_id, "campsiteName": "Pine Ridge",
        "checkIn": check_in, "checkOut": check_out, "status": status,
    })


def notices_for(db, recipient_id):
    return [notice.to_dict() for notice in db.collection("notifications").stream()
            if notice.to_dict().get("recipientId") == recipient_id]


def delete_campsite(db, campsite_id="pine"):
    """Deletes the campsite like an admin would, then runs the clean-up."""
    data = db.document(f"campsites/{campsite_id}").get().to_dict()
    db.document(f"campsites/{campsite_id}").delete()
    return main.clean_up_deleted_campsite(db, campsite_id, data, TODAY)


# clean_up_campsite


def test_campsite_cleanup_deletes_its_calendar(db):
    add_campsite(db)

    delete_campsite(db)

    assert not db.document("campsites/pine/availability/calendar").get().exists


def test_campsite_cleanup_deletes_its_photo_document(db):
    add_campsite(db, image_id="photo-1")
    db.document("images/photo-1").set({"ownerId": "owner-1"})

    delete_campsite(db)

    assert not db.document("images/photo-1").get().exists


def test_campsite_cleanup_takes_it_out_of_an_open_lottery_and_tells_the_waiting_list(db):
    add_campsite(db)
    add_lottery(db, "shared", ["pine", "cedar"], entries={"entrant-1": "WAITING"})

    delete_campsite(db)

    lottery = db.document("lotteries/shared").get().to_dict()
    assert lottery["campsiteIds"] == ["cedar"]
    assert lottery["status"] == "OPEN"
    [notice] = notices_for(db, "entrant-1")
    assert notice["type"] == "CAMPSITE_REMOVED"
    assert "still in the lottery" in notice["body"]


def test_campsite_cleanup_ends_a_lottery_it_was_the_only_campsite_of(db):
    add_campsite(db)
    add_lottery(db, "solo", ["pine"], status="DRAWN", entries={"winner": "WON", "booked": "BOOKED"})

    delete_campsite(db)

    lottery = db.document("lotteries/solo").get().to_dict()
    assert lottery["campsiteIds"] == []
    assert lottery["status"] == "ENDED"
    assert "has ended" in notices_for(db, "winner")[0]["body"]
    assert notices_for(db, "booked") == []


def test_campsite_cleanup_leaves_an_ended_lottery_alone(db):
    add_campsite(db)
    add_lottery(db, "old", ["pine"], status="ENDED", entries={"entrant-1": "NOT_SELECTED"})

    delete_campsite(db)

    assert db.document("lotteries/old").get().to_dict()["campsiteIds"] == ["pine"]
    assert notices_for(db, "entrant-1") == []


def test_campsite_cleanup_cancels_upcoming_reservations_and_tells_the_guests(db):
    add_campsite(db)
    add_reservation(db, "upcoming", "2026-12-10", "2026-12-12", guest="guest-1")
    add_reservation(db, "under-way", "2026-11-26", "2026-11-29", guest="guest-2")
    add_reservation(db, "past", "2026-10-01", "2026-10-03", guest="guest-3")
    add_reservation(db, "rejected", "2026-12-20", "2026-12-22", status="REJECTED", guest="guest-4")

    delete_campsite(db)

    def status(reservation_id):
        return db.document(f"reservations/{reservation_id}").get().to_dict()["status"]
    assert status("upcoming") == "CANCELLED"
    assert status("under-way") == "CANCELLED"
    assert status("past") == "CONFIRMED"
    assert status("rejected") == "REJECTED"
    assert db.document("reservations/upcoming").get().to_dict()["cancelledAt"] is not None
    assert "2026-12-10" in notices_for(db, "guest-1")[0]["body"]
    assert notices_for(db, "guest-3") == []
    assert notices_for(db, "guest-4") == []


def test_campsite_cleanup_sends_one_notice_each_even_when_run_twice(db):
    data = add_campsite(db)
    add_lottery(db, "shared", ["pine", "cedar"], entries={"guest-1": "WAITING"})
    add_reservation(db, "upcoming", "2026-12-10", "2026-12-12", guest="guest-1")
    db.document("campsites/pine").delete()

    first = main.clean_up_deleted_campsite(db, "pine", data, TODAY)
    main.clean_up_deleted_campsite(db, "pine", data, TODAY)

    assert first == ["guest-1", "owner-1"]
    assert len(list(db.collection("notifications").stream())) == 2
    # A guest's reservation is the reason that matters most to them.
    assert "reservation" in notices_for(db, "guest-1")[0]["body"]
    assert "removed your campsite" in notices_for(db, "owner-1")[0]["body"]


def test_campsite_trigger_passes_the_deleted_campsite(db, monkeypatch):
    calls = []
    monkeypatch.setattr(main.firestore, "client", lambda: db)
    monkeypatch.setattr(main, "clean_up_deleted_campsite", lambda *args: calls.append(args))
    snapshot = SimpleNamespace(to_dict=lambda: {"name": "Pine Ridge"})

    main.clean_up_campsite.__wrapped__(SimpleNamespace(data=snapshot, params={"campsiteId": "pine"}))

    assert calls[0][1:3] == ("pine", {"name": "Pine Ridge"})


# clean_up_profile


def test_profile_cleanup_reason_only_counts_the_change_into_each_state():
    assert main.profile_cleanup_reason({"name": "Ana"}, None) == "deleted"
    assert main.profile_cleanup_reason({"removed": False}, {"removed": True}) == "removed"
    assert main.profile_cleanup_reason({}, {"ownerBanned": True}) == "banned"
    assert main.profile_cleanup_reason({"removed": True}, {"removed": True, "name": ""}) is None
    assert main.profile_cleanup_reason({"name": "Ana"}, {"name": "Ana B"}) is None
    assert main.profile_cleanup_reason(None, {"name": "Ana"}) is None


def test_profile_cleanup_leaves_open_waiting_lists_and_declines_unbooked_wins(db):
    add_lottery(db, "open", ["pine"], entries={"gone": "WAITING", "other": "WAITING"})
    add_lottery(db, "drawn", ["pine"], status="DRAWN", entries={"gone": "WON"})
    add_lottery(db, "lost", ["pine"], status="DRAWN", entries={"gone": "NOT_SELECTED"})

    main.clean_up_profile_data(db, "gone", {"name": "Gone"}, TODAY, "deleted")

    assert not db.document("lotteries/open/entries/gone").get().exists
    assert db.document("lotteries/open").get().to_dict()["entryCount"] == 1
    assert db.document("lotteries/drawn/entries/gone").get().to_dict()["status"] == "DECLINED"
    assert db.document("lotteries/lost/entries/gone").get().to_dict()["status"] == "NOT_SELECTED"


def test_profile_cleanup_cancels_upcoming_stays_and_frees_their_nights(db):
    add_campsite(db)
    db.document("campsites/pine/availability/calendar").update(
        {"bookedNights": ["2026-10-01", "2026-12-10", "2026-12-11", "2026-12-20"]})
    add_reservation(db, "mine", "2026-12-10", "2026-12-12", guest="gone")
    add_reservation(db, "my-past", "2026-10-01", "2026-10-02", guest="gone")
    add_reservation(db, "theirs", "2026-12-20", "2026-12-21", guest="someone-else")

    main.clean_up_profile_data(db, "gone", {"name": "Gone"}, TODAY, "deleted")

    assert db.document("reservations/mine").get().to_dict()["status"] == "CANCELLED"
    assert db.document("reservations/my-past").get().to_dict()["status"] == "CONFIRMED"
    assert db.document("reservations/theirs").get().to_dict()["status"] == "CONFIRMED"
    nights = db.document("campsites/pine/availability/calendar").get().to_dict()["bookedNights"]
    assert nights == ["2026-10-01", "2026-12-20"]


def test_deleted_profile_loses_its_photo_and_campsites(db):
    db.document("images/face").set({"ownerId": "gone"})
    add_campsite(db, campsite_id="mine", owner="gone")
    add_campsite(db, campsite_id="theirs", owner="someone-else")

    main.clean_up_profile_data(db, "gone", {"name": "Gone", "imageId": "face"}, TODAY, "deleted")

    assert not db.document("images/face").get().exists
    assert not db.document("campsites/mine").get().exists
    assert db.document("campsites/theirs").get().exists


def test_removing_a_profile_that_was_deleted_meanwhile_still_finishes(db):
    main.clean_up_profile_data(db, "gone", {"name": "Gone"}, TODAY, "removed")

    assert not db.document("profiles/gone").get().exists


def test_removed_profile_keeps_only_its_roles_and_removed_flag(db):
    db.document("profiles/gone").set({
        "name": "Gone", "email": "gone@example.com", "phone": "7805550100", "pushToken": "token",
        "imageId": "face", "roles": ["ENTRANT"], "removed": True,
    })

    main.clean_up_profile_data(db, "gone", {"name": "Gone", "removed": False}, TODAY, "removed")

    profile = db.document("profiles/gone").get().to_dict()
    assert profile == {"name": "", "email": "", "phone": None, "pushToken": None, "imageId": None,
                       "roles": ["ENTRANT"], "removed": True}


def test_banned_owner_loses_their_campsites_but_stays_an_entrant(db):
    add_campsite(db, campsite_id="mine", owner="banned")
    add_lottery(db, "open", ["other"], entries={"banned": "WAITING"})
    add_reservation(db, "stay", "2026-12-10", "2026-12-12", campsite_id="other", guest="banned")

    main.clean_up_profile_data(db, "banned", {"name": "Banned"}, TODAY, "banned")

    assert not db.document("campsites/mine").get().exists
    assert db.document("lotteries/open/entries/banned").get().exists
    assert db.document("reservations/stay").get().to_dict()["status"] == "CONFIRMED"


def test_profile_trigger_ignores_ordinary_edits(db, monkeypatch):
    calls = []
    monkeypatch.setattr(main.firestore, "client", lambda: db)
    monkeypatch.setattr(main, "clean_up_profile_data", lambda *args: calls.append(args))

    def snapshot(data):
        return SimpleNamespace(exists=data is not None, to_dict=lambda: data)

    def write(before, after):
        change = SimpleNamespace(before=snapshot(before), after=snapshot(after))
        main.clean_up_profile.__wrapped__(SimpleNamespace(data=change, params={"uid": "user-1"}))

    write({"name": "Ana"}, {"name": "Ana B"})
    write({"name": "Ana"}, None)

    assert len(calls) == 1
    assert calls[0][1] == "user-1"
    assert calls[0][4] == "deleted"


# clean_up_image


def test_image_cleanup_deletes_the_file_and_clears_it_everywhere(db):
    add_campsite(db, image_id="photo-1")
    add_campsite(db, campsite_id="cedar", image_id="photo-2")
    db.document("profiles/ana").set({"name": "Ana", "imageId": "photo-1"})
    deleted_files = []

    main.clean_up_deleted_image(db, "photo-1", deleted_files.append)

    assert deleted_files == ["photo-1"]
    assert db.document("campsites/pine").get().to_dict()["imageId"] is None
    assert db.document("profiles/ana").get().to_dict()["imageId"] is None
    assert db.document("campsites/cedar").get().to_dict()["imageId"] == "photo-2"
