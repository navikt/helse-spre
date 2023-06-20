truncate table sendt_soknad;

alter table sendt_soknad add column hendelse_id uuid;

alter table sendt_soknad alter column hendelse_id set not null;