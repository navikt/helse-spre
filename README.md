# Spre
[![Build gosys](https://github.com/navikt/helse-spre/actions/workflows/gosys.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/gosys.yml)
[![Build oppgaver](https://github.com/navikt/helse-spre/actions/workflows/oppgaver.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/oppgaver.yml)
[![Build styringsinfo](https://github.com/navikt/helse-spre/actions/workflows/styringsinfo.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/styringsinfo.yml)
[![Build subsumsjon](https://github.com/navikt/helse-spre/actions/workflows/subsumsjon.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/subsumsjon.yml)
[![Build sykmeldt](https://github.com/navikt/helse-spre/actions/workflows/sykmeldt.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/sykmeldt.yml)

## Legge til en ny gradle-modul

Lag en mappe og sørg for at det finnes en `build.gradle.kts` der.

## Legge til ny app

Alle gradle-modulene bygges og releases automatisk. Ved hver pakke som blir lastet opp trigges en deployment workflow for
den pakken.

Appen heter `spre-[modul]` i Nais, mens modulen heter bare `[modul]`.

1. Gjør 'Legge til en ny gradle-modul'. Mappenavnet korresponderer med appnavnet
2. Lag `.nais/spre-[app].yaml` med det som er likt i alle miljøer, og `.nais/spre-[app].[miljø].yaml` (f.eks. `dev-gcp`, `prod-gcp`) med det som er miljøspesifikt.
3. Lag `.github/workflows/main-[app].yml` etter mønster fra de andre appene.
4. Lag en minimal `App.kt` så appen kan starte opp.
5. Push endringene

## Disable deploy av app eller begrense miljøer:

Det kan av forskjellige årsaker være nyttig å midlertidig skru av deploy av en app. Fjern eller kommenter ut
`deploy-[miljø]`-jobben i `.github/workflows/main-[app].yml`. Ikke gi mixin-fila (`.nais/spre-[app].[miljø].yaml`) nytt
navn – da deployes basen uten den miljøspesifikke konfigurasjonen.

## Oppgradering av gradle wrapper

Finn nyeste versjon av gradle her: https://gradle.org/releases/

`./gradlew wrapper --gradle-version $gradleVersjon`

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen [#team-sas-værsågod](https://nav-it.slack.com/archives/C019637N90X)
