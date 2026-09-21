from core.deid import scrub


def test_removes_ic_number_with_dashes():
    assert scrub("IC saya 590312-10-5566") == "IC saya [IC]"


def test_removes_ic_number_without_dashes():
    assert scrub("my ic is 590312105566 ok") == "my ic is [IC] ok"


def test_removes_malaysian_mobile_numbers_in_common_formats():
    for number in ["012-345 6789", "+60 12-345 6789", "0123456789", "+6019-8765432"]:
        assert scrub(f"call me at {number}") == "call me at [PHONE]", number


def test_removes_email_addresses():
    assert scrub("email nurul.aminah@gmail.com please") == "email [EMAIL] please"


def test_removes_known_names_case_insensitively():
    text = "Mak Cik AMINAH binti Yusof dah makan ubat"
    assert scrub(text, names=["Aminah binti Yusof"]) == "Mak Cik [NAME] dah makan ubat"


def test_removes_each_part_of_a_known_name_when_used_alone():
    assert scrub("Aminah rasa pening", names=["Aminah binti Yusof"]) == "[NAME] rasa pening"


def test_keeps_clinical_numbers_dates_and_times():
    text = "Metformin 500 mg BD, TCA 2/52, visit 21/09/26 at 10:05, HbA1c 8.2"
    assert scrub(text) == text


def test_ignores_short_name_parts_like_bin_and_binti():
    # "binti" alone must not be treated as a name, or ordinary words would vanish
    assert scrub("binti", names=["Aminah binti Yusof"]) == "binti"


def test_removes_malaysian_landline_numbers():
    assert scrub("klinik 03-7956 1234 buka") == "klinik [PHONE] buka"
