-- Condition pathways (diabetes, high blood pressure, after an operation) with the doctor's targets and review date.
create table patient_pathway (
    id uuid not null primary key,
    patient_id uuid not null,
    clinic_id uuid not null,
    pathway varchar(20) not null,
    glucose_max float(53),
    systolic_max integer,
    diastolic_max integer,
    hba1c_max float(53),
    review_every_weeks integer not null,
    next_review_on date,
    started_at timestamp(6) with time zone not null,
    started_by uuid not null,
    ended_at timestamp(6) with time zone
);
create index ix_patient_pathway_patient on patient_pathway (patient_id);
create index ix_patient_pathway_clinic on patient_pathway (clinic_id);

-- Laboratory results the doctor enters.
create table lab_result (
    id uuid not null primary key,
    patient_id uuid not null,
    test_code varchar(20) not null,
    result_value float(53) not null,
    taken_on date not null,
    recorded_by uuid not null,
    recorded_at timestamp(6) with time zone not null
);
create index ix_lab_result_patient on lab_result (patient_id);

-- A clinic's own points for each follow-up risk rule.
create table risk_weights (
    clinic_id uuid not null primary key,
    weights varchar(2000) not null,
    updated_by uuid,
    updated_at timestamp(6) with time zone
);
