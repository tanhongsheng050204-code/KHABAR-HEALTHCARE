-- Older readings have no trusted receipt time. Backfill measured_at as a conservative
-- approximation; new API submissions record measured_at and received_at separately.
alter table reading add column received_at timestamp(6) with time zone;
update reading set received_at = measured_at where received_at is null;
alter table reading alter column received_at set not null;
