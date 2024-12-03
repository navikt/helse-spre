alter table vedtak_fattet alter column opprettet set default now();
alter table utbetaling alter column opprettet set default now();

update vedtak_fattet set opprettet = opprettet + interval '1 hour';
update utbetaling set opprettet = opprettet + interval '1 hour';
