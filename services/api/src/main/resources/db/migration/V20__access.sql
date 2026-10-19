-- Voice-first and the simple large-print screen, chosen by the patient.
create table patient_preferences (
    patient_id uuid not null primary key,
    voice_first boolean not null,
    simple_mode boolean not null,
    updated_at timestamp(6) with time zone
);

-- A family Telegram group the patient linked for copies of their reminders and alerts.
create table family_group (
    patient_id uuid not null primary key,
    chat_enc varchar(512),
    chat_index varchar(64),
    linked_at timestamp(6) with time zone,
    link_code_hash varchar(64),
    link_code_expires_at timestamp(6) with time zone
);
create index ix_family_group_chat on family_group (chat_index);
create index ix_family_group_code on family_group (link_code_hash);

-- Who answered a dose reminder: the patient, a caregiver or the family group.
alter table dose_log add column answered_via varchar(20);
