# Khabar pitch drafts (3 / 5 / 7 minutes)

These are speaking drafts for the Khabar practice prototype, not an SDC submission. Personalize the opening,
team details, and demo before presenting. Do not claim clinical benefit, validated OCR, patient outcomes, or
production readiness without evidence. Keep the patient fictional and use demo data only.

Slide-ready content is in [PITCH_DECK_DRAFT.md](PITCH_DECK_DRAFT.md). It remains a local draft pending team/event details, the verified demo environment, and public-deck review.

## 3 minutes — the short version

**0:00–0:30 | The problem**

When a patient leaves a clinic, the visit is not really over. They still need to understand what to take, when
to take it, what warning signs matter, and who to contact if things change. Meanwhile, their medicines may come
from more than one clinic, a pharmacy, or a family remedy that never made it into the consultation.

**0:30–1:00 | The idea**

Khabar is a follow-up companion for Malaysian clinics. It helps the clinic prepare for a visit, checks a draft
prescription against the patient's recorded medicines and allergy context, then turns the approved prescription
into a plain-language care summary. It also supports check-ins and a clinic call list. The doctor remains in
control: Khabar does not prescribe, change doses, or replace clinical judgment.

**1:00–2:15 | Demo**

We’ll use Mak Cik Aminah, a fictional demo patient. First, we show the medicines and remedy list she has shared.
Next, the doctor writes a short note and reviews the generated draft. The safety panel can flag a duplicate or
interaction for the doctor to assess; a critical finding blocks finalisation until it is addressed or explicitly
overridden with a reason. Once the doctor approves the visit, Khabar builds a summary from the prescription
rather than asking a language model to invent instructions. Then we show the follow-up plan and the clinic's
call list. [If live messaging is not configured: “For this rehearsal, outbound messages are shown in the local
outbox; this is not a live WhatsApp delivery.”]

**2:15–2:45 | Why this approach**

The clinical record stays in the API. AI services receive de-identified context, and the patient graph uses a
random identifier rather than names or phone numbers. Some parts are rules-based on purpose: medicine safety
checks and medication instructions need to be inspectable, not just fluent.

**2:45–3:00 | Close**

Khabar is a working prototype of continuity after the appointment. The next step is not to claim impact; it is
to validate the workflow with clinicians and patients, complete provider setup, and test it safely with fake
data before considering any real-world pilot.

## 5 minutes — the product story

**0:00–0:40 | The moment care gets fragmented**

Imagine leaving a busy appointment with several instructions to remember. You may have a medicine from one
clinic, another from a pharmacy, and a traditional remedy at home. At the clinic, the doctor is trying to make
a safe decision with only the information they have been told. After the visit, the patient may still be unsure
what the plan means or when to ask for help.

**0:40–1:20 | What Khabar does**

Khabar is designed around the full loop: before the visit, during the visit, and the days after. Patients can
book and complete a guided intake. The doctor gets a pre-visit view, drafts the visit, and reviews safety
findings. After approval, the patient receives a summary in their chosen supported language and the clinic can
track follow-up. A consented caregiver can be included within the access the patient grants.

**1:20–3:40 | Demo walkthrough**

We’ll follow Mak Cik Aminah. She is fictional; this record is demo data.

First, the patient shares what she takes, including medicines from other places and remedies. In the current
prototype, she can type an item or use the packet-photo reader. The photo reader asks for consent before sending
the image to the clinic's configured Gemini service. It returns label text with evidence and a confidence level;
it does not add anything until the user reviews it. This feature still needs real-provider accuracy testing on
fictional packets.

Now the doctor opens the visit. The draft is generated from the doctor's notes. The safety checks compare the
draft with available allergy, pregnancy, medication, and interaction context. A warning is a prompt for review,
not a diagnosis. Critical findings block finalisation unless the doctor resolves them or records an override
reason. [Show one planted duplicate or interaction in the demo.]

After the doctor finalises, Khabar builds the patient summary from the approved prescription. The summary uses
fixed language templates so an AI model is not making up a dose or schedule. Finally, we move the demo clock or
show the follow-up view and the clinic call list. [State honestly whether messages went to the local outbox or
a configured test number.]

**3:40–4:30 | Safety and privacy choices**

The API is the system of record; the agent service does not connect directly to the clinical database. The
patient graph is designed around a random graph ID and de-identified facts. Role checks limit access, caregiver
consent can be revoked, and summaries are based on doctor-approved data. These are prototype controls that
still require deployed access testing; they are not a claim of certification or clinical validation.

**4:30–5:00 | Close and next step**

The prototype demonstrates a practical direction: make the care plan clearer and help the clinic see which
follow-up needs attention. Our next milestone is a complete deployed rehearsal, real provider verification,
and a small usability study. We will measure comprehension and report the results honestly before making
claims about benefit.

## 7 minutes — the fuller walkthrough

**0:00–0:45 | Opening**

The appointment is one moment in a much longer care journey. Patients go home with medicines, advice, and
questions. Their care may be spread across clinics, pharmacies, and family practices. The clinic needs a way
to understand what happens next without asking software to make clinical decisions on its behalf.

**0:45–1:35 | The product**

Khabar is a prototype for follow-up continuity in Malaysian primary care. It connects a guided patient intake,
a clinician-controlled visit workflow, a plain-language summary, and follow-up tracking. It is intentionally
not an autonomous prescriber. The doctor writes and approves the plan; Khabar helps organize context, flag
items to review, communicate the approved plan, and surface replies that may need a call.

**1:35–2:10 | Architecture in one minute**

The Spring Boot API owns the clinical record and access decisions. It sends only the context needed for a task
to the Python agent service. The graph stores de-identified facts under a random patient graph ID. The evaluator
uses maintained medicine data and explicit rules for checks; language models can help with selected text tasks,
but their output does not silently overrule the clinician or lower an urgent finding. The summary is built from
the approved prescription using reviewed templates.

**2:10–5:20 | Guided demo**

We’ll use Mak Cik Aminah, a fictional patient in the demo environment.

1. **Before the visit:** Open the intake and show how the patient describes what has changed. The pre-visit view
organizes answers, current medicines, remedies, allergy context, and warning symptoms for the doctor.
2. **Medication reconciliation:** Show the shared list. If demonstrating packet reading, use a fictional packet.
The patient sees a consent notice explaining that the image is sent to the clinic's configured Gemini service.
The output is just a proposed reading of visible text. Show the evidence, confidence, and type selector; add the
item only after review. If Gemini is not configured, skip the scan and enter the demo item manually.
3. **During the visit:** The doctor enters notes and gets a structured draft. The doctor checks the draft rather
than accepting it blindly. Open the safety review and show a planted duplicate or interaction. Explain that
clinical references can be incomplete: an unknown interaction is shown as a caution, not asserted to be a
confirmed harmful interaction.
4. **Approval:** Attempt finalisation with an open critical finding to show the block. Resolve it or enter the
required override reason, then finalise. The doctor owns that decision.
5. **After the visit:** Open the patient summary. Check each medicine, dose, timing, and warning against the
approved prescription. Show the follow-up schedule and a reply entering triage/call-list review. Clearly say
whether this is a local outbox simulation or a real provider test.

**5:20–6:10 | What is and is not proven**

The core local suites currently pass, but passing software tests is not the same as proving patient
understanding or clinical effectiveness. Deployed sign-in for every role, live WhatsApp and device-provider
loops, and user comprehension testing are still open. Packet-photo extraction has automated boundary tests but
has not yet been evaluated against a set of real-world packet images. We will not present those items as done.

**6:10–6:40 | How we will evaluate it**

The next checks are a fake-data end-to-end rehearsal, explicit allow-and-deny access checks for each role, and a
small comprehension pilot using the planned balanced two-case method. We will record failures as well as
successes, especially medicine name, timing, dose, and warning-symptom understanding.

**6:40–7:00 | Close**

Khabar is a prototype for helping care continue after a clinic visit. Our goal is to make the approved plan
easier to follow and the next concern easier for the clinic to see—while keeping the patient and clinician in
control. The work ahead is careful validation, not bigger claims.

## Rehearsal notes

- Replace bracketed demo notes with what is actually configured on presentation day.
- Avoid using real patient images, contact details, or records in the demo.
- If a provider is down, use the stated simulation fallback and keep presenting; do not imply a message was sent.
- Time each version aloud and cut examples rather than rushing the access/privacy explanation.
- Confirm every feature shown is available in the chosen environment and branch.
