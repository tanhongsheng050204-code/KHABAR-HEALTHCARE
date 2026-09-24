-- Keep the primary account persona, but model clinic staff capabilities as auditable
-- clinic-scoped grants so a clinic administrator can receive an additional clinical role.
alter table app_user drop constraint if exists app_user_role_check;
alter table app_user add constraint app_user_role_check
    check (role in ('DOCTOR', 'NURSE', 'CLINIC_ADMIN', 'PATIENT', 'CAREGIVER'));

alter table invite drop constraint if exists invite_kind_check;
alter table invite add constraint invite_kind_check
    check (kind in ('PATIENT_ACCOUNT', 'CAREGIVER', 'DOCTOR', 'STAFF'));
alter table invite add column staff_role varchar(255);
alter table invite add constraint invite_staff_role_check
    check (staff_role is null or staff_role in ('DOCTOR', 'NURSE', 'CLINIC_ADMIN'));

alter table medication_item drop constraint if exists medication_item_added_by_role_check;
alter table medication_item add constraint medication_item_added_by_role_check
    check (added_by_role in ('DOCTOR', 'NURSE', 'CLINIC_ADMIN', 'PATIENT', 'CAREGIVER'));

create table clinic_staff_grant (
    id uuid not null primary key,
    app_user_id uuid not null,
    clinic_id uuid not null,
    role varchar(255) not null check (role in ('DOCTOR', 'NURSE', 'CLINIC_ADMIN')),
    active_key varchar(120) unique,
    granted_at timestamp(6) with time zone not null,
    granted_by uuid,
    revoked_at timestamp(6) with time zone,
    revoked_by uuid,
    constraint fk_staff_grant_user foreign key (app_user_id) references app_user,
    constraint fk_staff_grant_clinic foreign key (clinic_id) references clinic
);
create index ix_staff_grant_clinic_active on clinic_staff_grant (clinic_id, revoked_at);

-- Existing doctors become explicit clinic members. granted_by is unknown for historical rows.
insert into clinic_staff_grant (id, app_user_id, clinic_id, role, active_key, granted_at)
select id, id, clinic_id, 'DOCTOR', cast(id as varchar) || ':' || cast(clinic_id as varchar) || ':DOCTOR', current_timestamp
from app_user
where role = 'DOCTOR' and clinic_id is not null;
