"""
Red-flag concepts for follow-up replies, in BM, English, Chinese and Tamil.

The word lists (data/triage_words.json) match whole phrases, so a patient who says it differently is missed:
on replies never used to tune them they caught 2 of 12 emergencies. These rules work one level up. A concept is
either a standalone sign ("pengsan", "叫不醒") or a pair: a place in the body and something wrong with it,
found anywhere in the same reply ("dada" ... "berat"; "呼吸" ... "困难"). They were written from common
red-flag lists for chest pain, breathlessness, stroke, collapse, bleeding, low blood sugar and confusion, not
from the held-out replies.

Like the word lists, they ignore negation on purpose: "tak sakit dada" still raises a flag, because a false
alarm costs a phone call and a missed one costs much more. Latin terms ending in * match as a prefix.
"""
import re
from dataclasses import dataclass
from typing import Optional

Terms = tuple[str, ...]


@dataclass(frozen=True)
class Concept:
    name: str
    level: str  # red | watch
    alone: Terms = ()  # any one of these is enough
    site: Terms = ()  # with `sign`: both must appear somewhere in the reply
    sign: Terms = ()
    cluster: Terms = ()  # at least `cluster_min` different ones must appear
    cluster_min: int = 2
    ignore: Terms = ()  # phrases removed before matching, e.g. "blood pressure" for the bleeding concept


CHEST = ("dada", "chest", "胸", "心口", "நெஞ்சு", "மார்பு")
CHEST_SIGN = ("sakit", "pedih", "berat", "ketat", "sesak", "cengkam*", "tekan*", "hempap", "pain*", "ache*", "hurt*",
              "tight*", "heav*", "pressure", "crush*", "squeez*", "burning", "痛", "闷", "紧", "压", "வலி", "இறுக்க", "அழுத்த", "கனமா")

BREATH = ("nafas", "bernafas", "breath*", "breathe", "呼吸", "மூச்சு")
BREATH_SIGN = ("susah", "sukar", "pendek", "sesak", "tak boleh", "tidak boleh", "hard*", "difficult*", "can't", "cannot", "can not",
               "hardly", "struggl*", "short", "gasp*", "困难", "不过", "不上", "不到", "急促", "முடியல", "முடியவில்லை", "திணற", "சிரம")

LIPS = ("bibir", "lips", "嘴唇", "உதடு")
BLUE = ("biru", "kebiruan", "blue", "发紫", "发青", "紫", "நீல")

BLOOD = ("darah", "blood*", "bleed*", "血", "ரத்த", "இரத்த")
BLOOD_SITE = ("muntah", "berak", "najis", "batuk", "kencing", "vomit*", "cough*", "stool", "poo", "urine", "吐", "咳", "大便", "小便", "尿",
              "வாந்தி", "மலம்", "இருமல்", "சிறுநீர்")

FACE = ("muka", "mulut", "face", "mouth", "嘴", "脸", "முகம்", "வாய்")
FACE_SIGN = ("senget", "herot", "jatuh", "droop*", "crooked", "歪", "下垂", "கோண", "சாய்")

SPEECH = ("cakap", "bercakap", "percakapan", "speech", "speak*", "talk*", "words", "说话", "讲话", "口齿", "பேச்சு", "பேச")
SPEECH_SIGN = ("pelat", "tak jelas", "tidak jelas", "tak keluar", "slur*", "garbled", "不清", "含糊", "说不出", "讲不出", "குழற")
# "can't" or "tak boleh" near "cakap" is too common ("doktor cakap tak boleh makan gula"), so only these exact phrases count.
SPEECH_ALONE = ("tak boleh cakap", "tak boleh bercakap", "tidak boleh bercakap", "can't speak", "cannot speak", "can't talk",
                "cannot talk", "trouble speaking", "trouble talking", "unable to speak", "பேச முடியல", "பேச முடியவில்லை")

ONE_SIDE = ("sebelah", "one side", "right arm", "left arm", "right leg", "left leg", "right side", "left side", "半边", "半身", "一边",
            "一侧", "ஒரு பக்க", "ஒரு பக்கம்")
WEAK = ("lemah", "tak boleh angkat", "tak boleh gerak", "kebas", "weak*", "numb*", "can't move", "cannot move", "can't lift",
        "没力", "无力", "麻", "动不了", "举不起", "பலவீன", "தூக்க முடியல", "அசைக்க முடியல", "மரத்து")

LOW_SUGAR = ("gula rendah", "gula jatuh", "hypo", "low sugar", "sugar is low", "sugar low", "低血糖", "血糖低", "சர்க்கரை குறை", "சர்க்கரை குறைஞ்சு")
AUTONOMIC = ("menggigil", "gigil", "menggeletar", "berpeluh", "peluh sejuk", "berdebar", "shak*", "trembl*", "sweat*",
             "palpitat*", "发抖", "颤抖", "冷汗", "心慌", "நடுங்க", "வியர்", "படபட")
# Everyday phrases that contain a blood word but are not bleeding.
NOT_BLEEDING = ("血糖", "血压", "低血", "高血", "darah tinggi", "tekanan darah", "gula dalam darah", "ujian darah", "blood pressure",
                "blood sugar", "blood test", "இரத்த அழுத்த", "ரத்த அழுத்த", "இரத்தச் சர்க்கரை", "ரத்த சர்க்கரை")

CONCEPTS: tuple[Concept, ...] = (
    Concept("chest pain or pressure", "red", site=CHEST, sign=CHEST_SIGN),
    Concept("trouble breathing", "red",
            alone=("tercungap", "semput", "short of breath", "breathless*", "out of breath", "喘不过气", "透不过气", "呼吸困难", "上气不接下气",
                   "மூச்சுத் திணறல்"),
            site=BREATH, sign=BREATH_SIGN),
    Concept("blue lips", "red", site=LIPS, sign=BLUE),
    Concept("collapse or not responding", "red",
            alone=("pengsan", "tak sedarkan diri", "tidak sedarkan diri", "tak sedar", "tak bangun", "tak menyahut", "tak jawab",
                   "collaps*", "faint*", "passed out", "unconscious", "unresponsive", "not responding", "can't wake", "cannot wake",
                   "won't wake", "wake him", "wake her", "晕倒", "昏倒", "昏迷", "叫不醒", "不省人事", "没反应", "மயங்கி", "மயக்கம் போட்",
                   "சுயநினைவு", "எழுப்ப முடியல")),
    Concept("bleeding", "red",
            alone=("berak hitam", "najis hitam", "black stool*", "tarry", "black poo", "黑便", "大便是黑", "黑色大便", "கருப்பு மலம்", "ரத்த வாந்தி"),
            site=BLOOD, sign=BLOOD_SITE, ignore=NOT_BLEEDING),
    Concept("face drooping", "red", site=FACE, sign=FACE_SIGN),
    Concept("speech trouble", "red", alone=SPEECH_ALONE, site=SPEECH, sign=SPEECH_SIGN),
    Concept("weakness on one side", "red", site=ONE_SIDE, sign=WEAK),
    Concept("low blood sugar", "red", alone=LOW_SUGAR),
    Concept("signs of low blood sugar", "red", cluster=AUTONOMIC, cluster_min=2),
    Concept("confusion or very drowsy", "red",
            alone=("keliru", "merepek", "tak kenal", "mamai", "confus*", "disorient*", "not making sense", "very drowsy",
                   "can't stay awake", "糊涂", "认不出", "神志不清", "胡言乱语", "குழப்ப", "அடையாளம் தெரியல")),
    # Watch: what the doctor should know about, today rather than now.
    Concept("swelling", "watch", alone=("bengkak", "swollen", "swelling", "puffy", "肿", "வீங்க", "வீக்கம்")),
    Concept("rash or itching", "watch", alone=("ruam", "bintik merah", "gatal", "rash*", "itch*", "hives", "红疹", "疹", "痒", "அரிப்பு", "தடிப்பு")),
    Concept("dizziness", "watch", alone=("pening", "pusing", "berpusing", "rasa ringan", "nak pitam", "pitam", "lightheaded", "light-headed",
                                         "dizz*", "faintish", "头晕", "晕", "தலை சுத்த", "தலைச்சுற்ற", "தலை லேசா")),
    Concept("stomach upset", "watch", alone=("loya", "muntah", "cirit", "berak air", "perut tak sedap", "nausea", "nauseous", "vomit*",
                                             "diarrh*", "loose stool*", "tummy upset", "upset stomach", "想吐", "恶心", "呕吐", "拉肚子", "腹泻",
                                             "வாந்தி", "வயிற்றுப்போக்கு", "குமட்டல்")),
    Concept("new cough", "watch", alone=("batuk", "cough*", "咳", "இருமல்")),
    Concept("high blood sugar", "watch", alone=("gula tinggi", "high sugar", "sugar is high", "高血糖", "血糖高", "சர்க்கரை அதிக")),
)

URGENCY = {"ok": 0, "review": 1, "watch": 2, "red": 3}
# A place and a sign count together only when this close (in characters), so "doktor cakap ... tak boleh makan"
# is not read as a speech problem.
NEAR = 30
# Bounds on the work for one reply: replies are short, and this keeps a pasted essay from costing much.
MAX_TEXT = 4000
MAX_SPANS = 50


@dataclass(frozen=True)
class ConceptHit:
    level: str
    concept: str
    evidence: str


def _latin(term: str) -> bool:
    return all(ord(ch) < 128 for ch in term)


def _spans(text: str, term: str) -> list[tuple[int, int]]:
    """Where a term occurs. Latin terms respect word starts (and word ends unless they end in *)."""
    if not _latin(term):
        pattern = re.escape(term)
    else:
        stem = term.rstrip("*")
        end = "" if term.endswith("*") else r"(?![a-z])"
        pattern = rf"(?<![a-z]){re.escape(stem)}{end}"
    spans = []
    for m in re.finditer(pattern, text):
        spans.append((m.start(), m.end()))
        if len(spans) >= MAX_SPANS:
            break
    return spans


def _find(text: str, term: str) -> Optional[str]:
    spans = _spans(text, term)
    return text[spans[0][0]:spans[0][1]] if spans else None


def _near_pair(text: str, sites: Terms, signs: Terms) -> Optional[str]:
    site_spans = [(a, b) for t in sites for a, b in _spans(text, t)]
    sign_spans = [(a, b) for t in signs for a, b in _spans(text, t)]
    for sa, sb in site_spans:
        for ga, gb in sign_spans:
            if (sa, sb) == (ga, gb):
                continue
            gap = ga - sb if ga >= sb else sa - gb
            if gap <= NEAR:
                return f"{text[sa:sb]} + {text[ga:gb]}"
    return None


def _any(text: str, terms: Terms) -> Optional[str]:
    for term in terms:
        found = _find(text, term)
        if found:
            return found
    return None


def detect(text: str) -> Optional[ConceptHit]:
    """The most urgent concept in the reply, or None. A long reply is read in overlapping pieces, all of it."""
    if len(text) <= MAX_TEXT:
        return _detect(text)
    # Never treated as fine: at least 'watch', and red if any piece is red.
    best = ConceptHit("watch", "long reply", f"{len(text)} characters")
    step = MAX_TEXT - NEAR - 50
    for start in range(0, len(text), step):
        hit = _detect(text[start:start + MAX_TEXT])
        if hit and URGENCY[hit.level] > URGENCY[best.level]:
            best = hit
            if best.level == "red":
                break
    return best


def _detect(text: str) -> Optional[ConceptHit]:
    best: Optional[ConceptHit] = None
    for c in CONCEPTS:
        lowered = text.lower()
        for phrase in c.ignore:
            lowered = lowered.replace(phrase, " " * len(phrase))
        evidence = None
        if c.alone:
            evidence = _any(lowered, c.alone)
        if evidence is None and c.site and c.sign:
            evidence = _near_pair(lowered, c.site, c.sign)
        if evidence is None and c.cluster:
            found = {t for t in c.cluster if _find(lowered, t)}
            if len(found) >= c.cluster_min:
                evidence = " + ".join(sorted(found))
        if evidence and (best is None or URGENCY[c.level] > URGENCY[best.level]):
            best = ConceptHit(c.level, c.name, evidence)
            if c.level == "red":
                return best
    return best
