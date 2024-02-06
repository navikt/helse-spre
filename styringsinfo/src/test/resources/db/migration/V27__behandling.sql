create table behandling(
    sekvensnummer   SERIAL PRIMARY KEY,
    sakId           UUID NOT NULL,
    behandlingId    UUID NOT NULL,
    funksjonellTid  TIMESTAMP NOT NULL,
    tekniskTid      TIMESTAMP NOT NULL,
    versjon         VARCHAR NOT NULL,
    data            JSONB NOT NULL
);

comment on column behandling.versjon is 'Versjon av data-kolonnen.';
comment on column behandling.data is 'En blob for aa unngaa problemer med datastream.';
-- Problem med datastream: Enkelte endringer på skjemaet støttes ikke i datastream
-- Dette gjelder eksempelvis endre datatype på en kolonne eller fjerne en kolonne
-- Se begrenser i "What are the limitations on the data that Datastream can process?" her https://cloud.google.com/datastream/docs/faq
-- Når vi gjør endringer i felter gjør vi derfor endringer i data-blobben samt øker versjon-feltet