CREATE TABLE vedtak_fattet
(
    id            UUID                     NOT NULL PRIMARY KEY,
    utbetaling_id UUID UNIQUE,
    fnr           VARCHAR(11)              NOT NULL,
    data          JSON                     NOT NULL,
    opprettet     TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc')
);

CREATE INDEX "index_vedtak_utbetaling" ON vedtak_fattet (utbetaling_id);
