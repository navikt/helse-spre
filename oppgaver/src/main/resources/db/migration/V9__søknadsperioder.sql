CREATE TABLE søknadsperioder
(
    hendelse_id UUID NOT NULL PRIMARY KEY,
    fom         DATE NOT NULL,
    tom         DATE NOT NULL,
    avsluttet   BOOLEAN DEFAULT FALSE,
    utbetaling  BOOLEAN DEFAULT FALSE
);