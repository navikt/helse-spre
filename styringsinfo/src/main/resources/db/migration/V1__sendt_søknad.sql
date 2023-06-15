CREATE TABLE sendt_soknad (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    sendt       TIMESTAMP NOT NULL,
    korrigerer  UUID,
    fnr         VARCHAR(11) NOT NULL,
    fom         DATE NOT NULL,
    tom         DATE NOT NULL,
    melding     JSON NOT NULL
);
