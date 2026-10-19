-- Doctor-approved diet tips per food group; a meal photo only ever gets these words back.
create table diet_tip (
    id uuid not null primary key,
    clinic_id uuid not null,
    category varchar(20) not null,
    text_ms varchar(1000) not null,
    text_en varchar(1000) not null,
    text_zh varchar(1000),
    text_ta varchar(1000),
    approved_by uuid not null,
    approved_at timestamp(6) with time zone not null,
    retired_at timestamp(6) with time zone
);
create index ix_diet_tip_clinic on diet_tip (clinic_id);
