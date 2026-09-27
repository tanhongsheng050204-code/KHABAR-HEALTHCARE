alter table patient_reply add column client_message_id uuid;

alter table patient_reply
    add constraint uk_patient_reply_client_message unique (patient_id, client_message_id);
