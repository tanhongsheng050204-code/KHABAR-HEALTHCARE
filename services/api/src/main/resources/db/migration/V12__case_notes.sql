-- Team notes on follow-up cases, with @mentions, and when each person last read their mentions.
create table case_note (
    id uuid not null primary key,
    case_id uuid not null,
    clinic_id uuid not null,
    author_id uuid not null,
    author_name varchar(255) not null,
    text_enc varchar(4096) not null,
    mentions varchar(2000),
    at timestamp(6) with time zone not null
);
create index ix_case_note_case on case_note (case_id);
create index ix_case_note_clinic on case_note (clinic_id);

create table mentions_seen (
    user_id uuid not null primary key,
    seen_at timestamp(6) with time zone
);
