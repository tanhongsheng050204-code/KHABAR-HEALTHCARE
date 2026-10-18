-- A take-home plan that fails the independent plan check is held: not sent and not shown to the patient
-- until a doctor releases it with a reason.
alter table visit_summary add column held_reason varchar(2000);
alter table visit_summary add column released_at timestamp(6) with time zone;
alter table visit_summary add column released_by uuid;
alter table visit_summary add column release_note varchar(500);
