CREATE TABLE søknad
(
    hendelse_id              UUID NOT NULL,
    dokument_id              UUID NOT NULL,
    mottatt_dato             TIMESTAMP,
    registrert_dato          TIMESTAMP,
    saksbehandler_ident      VARCHAR(10),
    PRIMARY KEY (dokument_id, hendelse_id)
);

CREATE INDEX søknad_hendelse_id_idx ON søknad (hendelse_id);

DROP TABLE hendelse;

DROP INDEX IF EXISTS hendelse_hendelse_id_idx;
