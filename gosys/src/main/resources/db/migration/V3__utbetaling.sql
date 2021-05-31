CREATE TABLE utbetaling
(
    id            UUID                     NOT NULL PRIMARY KEY,
    utbetaling_id UUID UNIQUE              NOT NULL,
    event         VARCHAR(32)              NOT NULL,
    fnr           VARCHAR(11)              NOT NULL,
    data          JSON                     NOT NULL,
    opprettet     TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc')
);

CREATE INDEX "index_utbetaling" ON utbetaling (utbetaling_id);