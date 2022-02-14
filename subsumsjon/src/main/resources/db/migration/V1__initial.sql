CREATE TABLE hendelse_id_mapping
(
    hendelse_id UUID,
    dokument_id UUID,
    hendelse_navn VARCHAR,
    publisert TIMESTAMP,
    PRIMARY KEY (hendelse_id, dokument_id, hendelse_navn, publisert)
);

CREATE INDEX hendelse_hendelse_id_idx ON hendelse_id_mapping (hendelse_id);
