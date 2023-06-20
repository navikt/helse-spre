CREATE TABLE vedtak_fattet (
    id                      UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    fnr                     VARCHAR(11) NOT NULL,
    fom                     DATE NOT NULL,
    tom                     DATE NOT NULL,
    vedtak_fattet_tidspunkt TIMESTAMP NOT NULL,
    hendelse_id             UUID NOT NULL,
    melding                 JSON NOT NULL
);
