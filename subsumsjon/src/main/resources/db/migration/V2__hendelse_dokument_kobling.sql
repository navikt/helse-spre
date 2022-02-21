CREATE TABLE hendelse_dokument_kobling
(
    hendelse_id UUID,
    dokument_id UUID,
    hendelse_type VARCHAR,
    publisert TIMESTAMP,
    PRIMARY KEY (hendelse_id, dokument_id)
);
