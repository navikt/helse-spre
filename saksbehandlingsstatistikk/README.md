spre-saksbehandlingsstatistikk
=======

## Antagelser:

Følgende rekkefølge på events forventes:

- Søknad, ref [SøknadRiver](src/main/kotlin/no/nav/helse/spre/saksbehandlingsstatistikk/SøknadRiver.kt)
- vedtaksperiode_endret
- enten
  - vedtak_fattet (når spleis avslutter på dirrern)
  - eller vedtaksperiode_avvist (utført av spesialist eller menneske)
    - deretter vedtaksperiode_forkastet
  - eller vedtaksperiode_godkjent (utført av spesialist eller menneske)
    - deretter vedtak_fattet

Sykmelding og inntektmelding ignoreres.

Søknader som ikke går inn i tilstandsmaskinen i spleis anses ikke som behandlet i ny løsning, og dermed ikke vil bli inkludert i datastrømmen ut fra denne appen. Disse går til Infotrygd.

### Følgende meldinger trigger at vi sender data til DVH:

* vedtak_fattet
* vedtaksperiode_forkastet
