CREATE TABLE annullering
(
    id                      UUID                     NOT NULL PRIMARY KEY,
    utbetaling_id           UUID UNIQUE              NOT NULL,
    fnr                     VARCHAR                  NOT NULL,
    organisasjonsnummer     VARCHAR                  NOT NULL,
    fom                     DATE                     NOT NULL,
    tom                     DATE                     NOT NULL,
    saksbehandler_ident      VARCHAR                  NOT NULL,
    saksbehandler_epost      VARCHAR                  NOT NULL,
    person_fagsystem_id       VARCHAR,
    arbeidsgiver_fagsystem_id VARCHAR,
    opprettet               TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc')
);

CREATE INDEX "index_fnr_orgnr" ON annullering (fnr, organisasjonsnummer);