-- A patient's latest Telegram voice-note transcript, waiting for them to tap Send.
create table voice_draft (
    patient_id uuid not null primary key,
    text_enc varchar(8192) not null,
    source_message_id varchar(100),
    created_at timestamp(6) with time zone not null
);

-- A packet photo waiting for the patient's consent before it is read: Telegram's file id only, never the image.
create table photo_draft (
    patient_id uuid not null primary key,
    file_id varchar(300) not null,
    created_at timestamp(6) with time zone not null
);
