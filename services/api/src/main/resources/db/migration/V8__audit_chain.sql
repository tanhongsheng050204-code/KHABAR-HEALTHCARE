-- The record-access log becomes a hash chain: each sealed entry stores its position, the previous entry's
-- hash and its own. Entries written before this migration stay unsealed until the sealer links them in.
alter table audit_log add column chain_seq bigint;
alter table audit_log add column prev_hash varchar(64);
alter table audit_log add column hash varchar(64);
create unique index ux_audit_log_chain_seq on audit_log (chain_seq);

create table audit_chain_head (
    id integer not null primary key,
    last_seq bigint not null,
    last_hash varchar(64) not null,
    sealed_at timestamp(6) with time zone
);
