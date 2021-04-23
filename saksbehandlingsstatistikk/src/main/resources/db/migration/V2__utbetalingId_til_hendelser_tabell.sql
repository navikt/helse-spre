CREATE TABLE soknad_kobling
(
    id                  SERIAL PRIMARY KEY,
    soknad_id           UUID NOT NULL,
    utbetaling_id       UUID,
    vedtaksperiode_id   UUID,
    tidspunkt           TIMESTAMP DEFAULT NOW()
);
