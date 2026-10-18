-- The end-of-shift handover to the staff chat.
alter table staff_channel add column handover_hour integer;
alter table staff_channel add column last_handover_date date;
