-- Telegram replaces WhatsApp: the patient's Telegram chat, encrypted, and a keyed hash to find it.
alter table patient add column telegram_chat_enc varchar(512);
alter table patient add column telegram_chat_index varchar(64);
create index idx_patient_telegram_chat_index on patient (telegram_chat_index);
