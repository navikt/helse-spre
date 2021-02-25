Spre [![Build](https://github.com/navikt/helse-spre/actions/workflows/build.yml/badge.svg)](https://github.com/navikt/helse-spre/actions/workflows/build.yml)
=======

## Legge til en ny gradlemodul

1. Lag en mappe og sørg for at det finnes en `build.gradle.kts` der

## Legge til ny app

Alle gradlemodulene bygges og releases automatisk. 
Ved hver pakke som blir lastet opp trigges en deployment workflow for den pakken.

Navnet på appen prefikses med `spre-` i nais.yml, slik at navnet på 
modulen skal være uten.

1. Gjør 'Legge til en ny gradlemodul'. Mappenavnet korresponderer med appnavnet
2. Lag `config/[app]/[cluster].yml` for de klustrene appen skal deployes til. 
3. Push endringene

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod
