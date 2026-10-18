-- The no-reply sequence: when the one gentle re-ask for an unanswered check-in went out.
alter table check_in add column reminder_sent_at timestamp(6) with time zone;
