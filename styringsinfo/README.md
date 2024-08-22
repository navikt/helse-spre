# Styringsinfo

Denne appen er ansvarlig for å snuse på hendelser som kan være interessante for nedstrøms-data-analyse. Formålet er å hjelpe styringsenheten(e?) å ta gode og rette avgjørelser.

## Håndtere feilsitasjon ved manglende tags

Hva gjør man ved feilsitasjonen `Feil ved håndtering av vedtak_fattet: Nå kom det jaggu et event...`

1. Søk opp i Kibana `application:spre-styringsinfo AND "Feil ved håndtering av vedtak_fattet"`
2. Trykk på `Toggle dialog with details` helt til venstre på logglinjen
3. Kopier `x_id` og stapp den inn som key i mapet `manuelleTags` i `VedtakFattet`-klassen
4. Kopier `x_behandlingId` fra punkt 2 og søk opp følgende i Kibana: `application:spre-styringsinfo AND "Håndterte utkast_til_vedtak" AND "<x_behandlingId>"` 
   - I denne meldingen vil du se alle taggene som skulle vært med i `vedtak_fattet`
   - P.S velg den seneste om de det er fler 
   - P.S.S Her kan det være at du må utvide tidsintervallet på søket for å få treff, avhengig av hvor lenge det har feilet.  
5. Kopier taggene  du fant frem i punkt 4 og lim de inn som value i mapet `manuelleTags` i `VedtakFattet`-klassen i dette prosjektet
   - Taggene skal limes inn i samme entry som du allerede har besudlet key'en til `x_id` fra punkt 3. 
   - Eksempelvis som gjort [her](https://github.com/navikt/helse-spre/commit/cf26da42fd186e2d514f15d8045624910938b5f0)