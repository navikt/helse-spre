CREATE TABLE planlagt_annullering
(
    id                  UUID                     NOT NULL PRIMARY KEY,
    fnr                 VARCHAR                  NOT NULL,
    yrkesaktivitet      VARCHAR                  NOT NULL,
    fom                 DATE                     NOT NULL,
    tom                 DATE                     NOT NULL,
    saksbehandler_ident VARCHAR                  NOT NULL,
    arsaker             TEXT[]                   NOT NULL,
    begrunnelse         VARCHAR                  NOT NULL,
    annullert           TIMESTAMP WITH TIME ZONE,
    opprettet           TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc')
);

CREATE TABLE vedtaksperioder_som_skal_annulleres
(
    vedtaksperiode_id UUID NOT NULL,
    plan              UUID NOT NULL REFERENCES planlagt_annullering,
    annullert         TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (vedtaksperiode_id, plan)
)