-- What each patient chose to be reminded about on Telegram. Everything is off until the patient switches it on.
create table reminder_settings (
    patient_id uuid not null primary key,
    dose_reminders boolean not null,
    appointment_reminders boolean not null,
    updated_at timestamp(6) with time zone
);

-- One dose reminder per patient, day and time of day, with the patient's Taken / Missed answer.
create table dose_log (
    id uuid not null primary key,
    patient_id uuid not null,
    encounter_id uuid not null,
    dose_day date not null,
    slot varchar(20) not null,
    medicines_enc varchar(4096) not null,
    sent_at timestamp(6) with time zone not null,
    delivered boolean not null,
    response varchar(10),
    responded_at timestamp(6) with time zone,
    constraint ux_dose_log_slot unique (patient_id, dose_day, slot)
);
create index ix_dose_log_patient on dose_log (patient_id);

-- Refill and appointment reminders, each sent once, with the patient's answer.
create table reminder_log (
    id uuid not null primary key,
    patient_id uuid not null,
    kind varchar(20) not null,
    ref uuid not null,
    sent_at timestamp(6) with time zone not null,
    delivered boolean not null,
    response varchar(20),
    responded_at timestamp(6) with time zone,
    constraint ux_reminder_log_ref unique (kind, ref)
);

-- A booked visit the patient did not come to; on the call list until someone records contact.
create table appointment_outcome (
    appointment_id uuid not null primary key,
    patient_id uuid not null,
    clinic_id uuid not null,
    outcome varchar(20) not null,
    appointment_at timestamp(6) with time zone not null,
    detected_at timestamp(6) with time zone not null,
    handled_at timestamp(6) with time zone,
    handled_by uuid
);
create index ix_appointment_outcome_clinic on appointment_outcome (clinic_id);

-- A caregiver's own Telegram chat for alerts, linked with a short-lived code.
create table caregiver_telegram (
    caregiver_id uuid not null primary key,
    chat_enc varchar(512),
    chat_index varchar(64),
    linked_at timestamp(6) with time zone,
    link_code_hash varchar(64),
    link_code_expires_at timestamp(6) with time zone
);
create index ix_caregiver_telegram_chat on caregiver_telegram (chat_index);
create index ix_caregiver_telegram_code on caregiver_telegram (link_code_hash);

-- Each alert sent to a caregiver, once per event.
create table caregiver_notice (
    id uuid not null primary key,
    caregiver_id uuid not null,
    patient_id uuid not null,
    kind varchar(20) not null,
    ref uuid not null,
    sent_at timestamp(6) with time zone not null,
    delivered boolean not null,
    constraint ux_caregiver_notice unique (caregiver_id, kind, ref)
);
