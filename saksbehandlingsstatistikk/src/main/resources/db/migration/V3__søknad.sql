CREATE TABLE søknad
(
    hendelse_id              UUID NOT NULL,
    vedtaksperiode_id        UUID,
    dokument_id              UUID,
    mottatt_dato             TIMESTAMP,
    registrert_dato          TIMESTAMP,
    saksbehandler_ident      VARCHAR(10),
    PRIMARY KEY (hendelse_id)
);

CREATE INDEX søknad_hendelse_id_idx ON søknad (hendelse_id);
CREATE INDEX søknad_vedtaksperiode_id_idx ON søknad (vedtaksperiode_id);

DROP TABLE hendelse;

DROP INDEX IF EXISTS hendelse_hendelse_id_idx;

DROP TABLE soknad_kobling
