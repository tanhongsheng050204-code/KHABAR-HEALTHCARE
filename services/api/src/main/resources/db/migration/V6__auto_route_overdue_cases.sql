alter table follow_up_case add column auto_routed_at timestamp(6) with time zone;
alter table follow_up_case add column auto_routed_to uuid;
