# Styringsinfo

Denne appen er ansvarlig for å snuse på hendelser som kan være interessante for nedstrøms-data-analyse. Formålet er å hjelpe styringsenheten(e?) å ta gode og rette avgjørelser.

## Håndtere feilsitasjon ved manglende tags

Hva gjør man ved feilsitasjonen `Feil ved håndtering av vedtak_fattet: Nå kom det jaggu et event...`

1. Søk opp i Kibana `application:spre-styringsinfo AND "Feil ved håndtering av vedtak_fattet"`
2. Trykk på `Toggle dialog with details` helt til venstre på logglinjen
3. Ta var på verdiene til `x_behandlingId` & `x_id` 
4. Søk opp i Kibana `application:spre-styringsinfo AND "Håndterte utkast_til_vedtak" AND "<x_behandlingId-fra-punkt-3>"` og velg den seneste om de det er fler (Her kan det være at du må utvide tidsintervallet på søket for å få treff, avhengig av hvor lenge det har feilet.)
5. I denne meldingen vil du se alle taggene som skulle vært med i `vedtak_fattet`
6. Legg disse taggene manuelt inn i `VedtakFattet`-klassen i dette prosjektet, hvor key'en i mapet er `x_id` fra punkt 3. Eksempelvis som gjort [her](https://github.com/navikt/helse-spre/commit/cf26da42fd186e2d514f15d8045624910938b5f0)