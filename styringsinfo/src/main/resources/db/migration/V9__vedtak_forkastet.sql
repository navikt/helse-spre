CREATE TABLE vedtak_forkastet (
    id                  UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    fnr                 VARCHAR(11) NOT NULL,
    fom                 DATE NOT NULL,
    tom                 DATE NOT NULL,
    forkastet_tidspunkt TIMESTAMP NOT NULL,
    hendelse_id         UUID NOT NULL,
    melding             JSON NOT NULL
);
COMMENT ON COLUMN vedtak_forkastet.forkastet_tidspunkt IS 'Hentes fra feltet @opprettet i selve meldingen';
