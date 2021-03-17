CREATE TABLE hendelse
(
    dokument_id UUID,
    hendelse_id UUID,
    type        VARCHAR,
    PRIMARY KEY (dokument_id, hendelse_id, type)
);

CREATE INDEX hendelse_hendelse_id_idx ON hendelse (hendelse_id);
