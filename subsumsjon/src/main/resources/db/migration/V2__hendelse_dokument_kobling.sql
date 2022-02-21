CREATE TABLE hendelse_dokument_kobling
(
    hendelse_id UUID,
    dokument_id UUID,
    hendelse_type VARCHAR,
    publisert TIMESTAMP,
    PRIMARY KEY (hendelse_id, dokument_id)
);

INSERT INTO hendelse_dokument_kobling (hendelse_id, dokument_id, hendelse_type, publisert)
SELECT hendelse_id, dokument_id, hendelse_navn, publisert from hendelse_id_mapping
ON CONFLICT DO NOTHING;
