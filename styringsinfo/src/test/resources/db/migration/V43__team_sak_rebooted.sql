drop table hendelse cascade;
drop table behandlingshendelse cascade;

create table hendelse(
   id           UUID PRIMARY KEY,
   opprettet    TIMESTAMPTZ(6) NOT NULL,
   type         VARCHAR NOT NULL,
   data         JSONB NOT NULL
);
create table behandlingshendelse(
    sekvensnummer   SERIAL PRIMARY KEY,
    sakId           UUID NOT NULL,
    behandlingId    UUID NOT NULL,
    funksjonellTid  TIMESTAMPTZ(6) NOT NULL,
    tekniskTid      TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    versjon         VARCHAR NOT NULL,
    siste           BOOLEAN NOT NULL,
    data            JSONB NOT NULL,
    hendelseId      UUID NOT NULL REFERENCES hendelse(id),
    er_korrigert    BOOLEAN NOT NULL DEFAULT FALSE
);

comment on column behandlingshendelse.versjon is 'Versjon av data-kolonnen.';
comment on column behandlingshendelse.data is 'En blob for aa unngaa problemer med datastream.';
comment on column behandlingshendelse.tekniskTid is 'Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.';
-- Problem med datastream: Enkelte endringer på skjemaet støttes ikke i datastream
-- Dette gjelder eksempelvis endre datatype på en kolonne eller fjerne en kolonne
-- Se begrenser i "What are the limitations on the data that Datastream can process?" her https://cloud.google.com/datastream/docs/faq
-- Når vi gjør endringer i felter gjør vi derfor endringer i data-blobben samt øker versjon-feltet

create index idx_sisteBehandlingerPerSak on behandlingshendelse(sakId, siste);
create index idx_sisteBehandling on behandlingshendelse(behandlingId, siste);
