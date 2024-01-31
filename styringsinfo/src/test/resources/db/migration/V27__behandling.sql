create table behandling(
    sakId           UUID NOT NULL,
    behandlingId    UUID NOT NULL,
    funksjonellTid  TIMESTAMP NOT NULL,
    tekniskTid      TIMESTAMP NOT NULL,
    versjon         VARCHAR NOT NULL,
    data            JSONB NOT NULL
);

comment on column behandling.versjon is 'Versjon av data-kolonnen.';
comment on column behandling.data is 'En blob for aa unngaa problemer med datastream.';