-- PDPA rights: a patient's erasure or correction requests, and the clinic's register of data breaches.
create table privacy_request (
    id uuid not null primary key,
    clinic_id uuid not null,
    patient_id uuid not null,
    kind varchar(20) not null,
    status varchar(20) not null,
    details_enc varchar(4096),
    created_at timestamp(6) with time zone not null,
    resolved_at timestamp(6) with time zone,
    resolved_by uuid,
    resolution varchar(1000)
);
create index ix_privacy_request_clinic on privacy_request (clinic_id);

create table privacy_incident (
    id uuid not null primary key,
    clinic_id uuid not null,
    title varchar(200) not null,
    what_enc varchar(8192),
    people_affected integer,
    discovered_at timestamp(6) with time zone not null,
    reported_by uuid not null,
    contained_at timestamp(6) with time zone,
    assessed_at timestamp(6) with time zone,
    commissioner_notified_at timestamp(6) with time zone,
    people_notified_at timestamp(6) with time zone,
    closed_at timestamp(6) with time zone,
    significant_harm boolean not null,
    log varchar(2000)
);
create index ix_privacy_incident_clinic on privacy_incident (clinic_id);
