Spre [![Build](https://github.com/navikt/helse-spre/actions/workflows/build.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/build.yml)
=======

## Legge til en ny gradlemodul

1. Lag en mappe og sørg for at det finnes en `build.gradle.kts` der

## Legge til ny app

Alle gradlemodulene bygges og releases automatisk. Ved hver pakke som blir lastet opp trigges en deployment workflow for
den pakken.

Navnet på appen prefikses med `spre-` i nais.yml, slik at navnet på modulen skal være uten.

1. Gjør 'Legge til en ny gradlemodul'. Mappenavnet korresponderer med appnavnet
2. Lag `config/[app]/[cluster].yml` for de klustrene appen skal deployes til.
3. Lag en minimal `App.kt` så appen kan starte opp. 
4. Push endringene

## Disable deploy av app eller begrense miljøer:

Det kan av forskjellige årsaker være nyttig å midlertidig skru av deploy av en app. Enkleste måte å gjøre det på er å
legge til noe etter .yml i filendelsen på det aktuelle miljøet i `config/[app]/[cluster].yaml`. Her er et eksempel hvor
vi disabler deploy av spre-gosys i
prod: https://github.com/navikt/helse-spre/commit/19424c6edb195dbcb06d1f6f1d4bcd6267ed685e.

## Oppgradering av gradle wrapper
Finn nyeste versjon av gradle her: https://gradle.org/releases/

```./gradlew wrapper --gradle-version $gradleVersjon```

Husk å oppdater gradle versjon i build.gradle.kts filen
```val gradlewVersion = "$gradleVersjon"```

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod
