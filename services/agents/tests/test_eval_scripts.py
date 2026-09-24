"""The evaluation scripts score correctly, so a real-provider run can be trusted without re-checking by hand."""
from agents.packet_reader import PacketCandidate, PacketReadResult
from scripts.eval_packet_reader import score_packet
from scripts.score_transcripts import drug_name_errors

TWO = {"expect_generics": ["aspirin", "simvastatin"], "expect_herbs": [], "unreadable": False}


def candidate(generic=None, kind="MEDICINE", text=""):
    return PacketCandidate(kind=kind, ingredient_text=text, generic_candidate=generic, confidence="high")


def test_a_packet_passes_when_everything_expected_is_found():
    result = PacketReadResult(candidates=[candidate("aspirin"), candidate("simvastatin")])
    assert score_packet(TWO, result)["passed"]


def test_a_missed_or_invented_generic_fails_the_packet():
    missed = score_packet(TWO, PacketReadResult(candidates=[candidate("aspirin")]))
    invented = score_packet(TWO, PacketReadResult(candidates=[candidate("aspirin"), candidate("simvastatin"), candidate("warfarin")]))
    assert (missed["passed"], missed["missing"]) == (False, ["simvastatin"])
    assert (invented["passed"], invented["invented"]) == (False, ["warfarin"])


def test_a_herb_counts_only_when_kept_as_a_herb():
    herb = {"expect_generics": [], "expect_herbs": ["bitter gourd"], "unreadable": False}
    assert score_packet(herb, PacketReadResult(candidates=[candidate(kind="HERB", text="Bitter gourd extract")]))["passed"]


def test_a_blurred_packet_must_be_reported_unreadable_not_guessed():
    blurred = {"expect_generics": [], "expect_herbs": [], "unreadable": True}
    assert score_packet(blurred, PacketReadResult(unreadable=True))["passed"]
    assert not score_packet(blurred, PacketReadResult(candidates=[candidate("metformin")]))["passed"]


def test_drug_names_are_compared_not_just_words():
    errors = drug_name_errors("T. Metformin 500mg BD, T. Amlodipine 5mg OD", "T. met for min 500mg BD, T. Amlodipine 5mg OD")
    assert errors == {"missed": ["metformin"], "wrong": []}


def test_a_drug_heard_as_another_drug_is_the_worst_error():
    errors = drug_name_errors("T. Gliclazide 80mg OD", "T. Glipizide 80mg OD, T. Aspirin")
    assert errors["missed"] == ["gliclazide"]
    assert errors["wrong"] == ["aspirin"]
