from agents.prescription import parse_line
from agents.summary import build_summary

METFORMIN = parse_line("T. Metformin 500mg 1/1 BD PC")
GLICLAZIDE = parse_line("T. Gliclazide 80mg 1/1 OM AC")
PARACETAMOL_PRN = parse_line("T. Paracetamol 500mg 2/1 PRN")
TDS_DRUG = parse_line("T. Metformin 500mg 1/1 TDS PC")


def line(summary, i=0):
    return summary.medicines[i].how


def test_bm_summary_reads_like_the_landing_page():
    s = build_summary([METFORMIN], "ms", follow_up_weeks=2)
    assert s.medicines[0].medicine == "Metformin 500 mg"
    assert line(s) == "1 biji, pagi dan malam, selepas makan."
    assert s.next_visit == "Datang semula dalam 2 minggu."


def test_english_summary():
    s = build_summary([GLICLAZIDE], "en")
    assert line(s) == "1 tablet, every morning, before food."


def test_chinese_summary():
    s = build_summary([METFORMIN], "zh")
    assert line(s) == "每次1粒，早上和晚上，饭后服用。"


def test_tamil_summary():
    s = build_summary([METFORMIN], "ta")
    assert line(s) == "1 மாத்திரை, காலை மற்றும் இரவு, சாப்பிட்ட பிறகு."


def test_two_tablets_and_only_when_needed():
    assert line(build_summary([PARACETAMOL_PRN], "en")) == "2 tablets, only when needed."


def test_every_summary_carries_the_come_back_warning():
    for lang in ("ms", "en", "zh", "ta"):
        assert build_summary([METFORMIN], lang).warning


def test_follow_up_under_a_week_is_said_in_days():
    assert build_summary([METFORMIN], "en", follow_up_weeks=3 / 7).next_visit == "Come back in 3 days."


def test_fasting_mode_moves_twice_daily_doses_to_sahur_and_berbuka():
    s = build_summary([METFORMIN], "ms", fasting=True)
    assert line(s) == "1 biji, waktu sahur dan waktu berbuka, selepas makan."
    assert s.needs_doctor == []


def test_fasting_mode_never_guesses_for_three_doses_a_day():
    s = build_summary([TDS_DRUG], "en", fasting=True)
    assert line(s) == "Ask your doctor how to take this while fasting."
    assert s.needs_doctor == ["Metformin 500 mg"]


def test_whatsapp_text_joins_everything_in_order():
    s = build_summary([METFORMIN, GLICLAZIDE], "ms", follow_up_weeks=2)
    text = s.as_text()
    assert text.index("Metformin") < text.index("Gliclazide") < text.index("Datang semula")
