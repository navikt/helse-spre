CREATE TABLE generasjon_opprettet
(
    id                  UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    aktørId             VARCHAR(255) NOT NULL,
    generasjonId        UUID         NOT NULL,
    vedtaksperiodeId    UUID         NOT NULL,
    type                VARCHAR(255) NOT NULL,
    avsender            varchar(255) NOT NULL,
    meldingsreferanseId UUID         NOT NULL,
    innsendt            timestamp    NOT NULL,
    registrert          timestamp    NOT NULL,
    hendelseId          UUID         NOT NULL
);

