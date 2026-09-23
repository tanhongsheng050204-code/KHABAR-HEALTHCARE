"""
From a packet photo to a safety finding: the label is read (the provider is stubbed at its HTTP
boundary), the reviewed item joins the medicine list the way the web app adds it, and the checks
that already guard typed items catch the duplicate and the herb clash.
"""
import json

import httpx

from agents.evaluator import CurrentMed, Draft, PatientFacts, Rx, evaluate, reconcile
from agents.packet_reader import PacketCandidate, read_packet

PHOTO_SOURCE = "Packet photo — please verify"


def provider_reads(*labels):
    text = json.dumps({"candidates": list(labels), "unreadable": False, "message": ""})

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": text}]}}]})

    return httpx.Client(transport=httpx.MockTransport(handler))


def as_the_web_app_adds_it(item: PacketCandidate) -> str:
    """web/components/home/packet-photo-reader.tsx: the generic if known, else the label text, plus the strength."""
    name = item.generic_candidate or item.ingredient_text or item.brand
    return " ".join(part for part in (name, item.strength) if part)


BRAND_A_PACKET = {"kind": "MEDICINE", "brand": "Brand A", "ingredient_text": "", "strength": "500 mg",
                  "form": "tablet", "confidence": "high", "evidence": "Brand A 500 mg"}
PERIA_CAPSULES = {"kind": "HERB", "brand": "Kapsul Peria", "ingredient_text": "Bitter gourd (peria) extract",
                  "strength": "", "form": "capsule", "confidence": "medium", "evidence": "Kapsul Peria bitter gourd"}


def read(*labels):
    return read_packet(b"\xff\xd8\xff fake jpeg", "image/jpeg", api_key="test-key", model="test-model",
                       client=provider_reads(*labels)).candidates


def test_a_brand_read_from_a_packet_is_caught_as_a_duplicate_of_the_same_drug_from_another_clinic():
    packet = read(BRAND_A_PACKET)[0]
    assert packet.generic_candidate == "metformin"

    listed = [CurrentMed(name="Metformin 500mg", source="Klinik Kesihatan"),
              CurrentMed(name=as_the_web_app_adds_it(packet), source=PHOTO_SOURCE)]
    findings = reconcile(listed, [])

    duplicate = next(f for f in findings if f.check == "duplicate")
    assert duplicate.severity == "CRITICAL"
    assert "Klinik Kesihatan" in duplicate.detail and PHOTO_SOURCE in duplicate.detail


def test_a_herbal_capsule_read_from_a_packet_clashes_with_a_new_prescription():
    capsule = read(PERIA_CAPSULES)[0]
    assert capsule.kind == "HERB"

    draft = Draft(patient=PatientFacts(), prescription=[Rx(name="Gliclazide 80mg", dose_mg=80, times_per_day=1)],
                  herbs=[as_the_web_app_adds_it(capsule)],
                  report={"diagnosis": "T2DM", "plan": "Start gliclazide", "follow_up": "4 weeks"})
    herb = next(f for f in evaluate(draft) if f.check == "herb")
    assert "Bitter Gourd" in herb.detail and "Gliclazide" in herb.detail


def test_prescribing_a_drug_the_packet_shows_the_patient_already_takes_is_blocked():
    packet = read(BRAND_A_PACKET)[0]
    draft = Draft(prescription=[Rx(name="Metformin 500mg", dose_mg=500, times_per_day=2)],
                  current_meds=[CurrentMed(name=as_the_web_app_adds_it(packet), source=PHOTO_SOURCE)],
                  report={"diagnosis": "T2DM", "plan": "Metformin", "follow_up": "4 weeks"})
    findings = evaluate(draft)
    assert any(f.check == "duplicate" and f.severity == "CRITICAL" and PHOTO_SOURCE in f.detail for f in findings)


def test_an_unreadable_label_gives_nothing_to_add_so_nothing_is_guessed():
    candidates = read({"kind": "UNSURE", "brand": "", "ingredient_text": "", "strength": "", "form": "",
                       "confidence": "low", "evidence": ""})
    assert candidates[0].generic_candidate is None
    assert as_the_web_app_adds_it(candidates[0]) == ""
