-- A patient's latest Telegram voice-note transcript, waiting for them to tap Send.
create table voice_draft (
    patient_id uuid not null primary key,
    text_enc varchar(8192) not null,
    source_message_id varchar(100),
    created_at timestamp(6) with time zone not null
);
