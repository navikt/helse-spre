package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*

class VedtaksperiodeGodkjentData(
    val vedtaksperiodeId: UUID,
    val saksbehandlerIdent: String
)

/*
"vedtaksperiode_godkjent", fødselsnummer).apply { this.putAll(mapOf(
            "vedtaksperiodeId" to vedtaksperiodeId,
            "warnings" to warnings,
            "periodetype" to periodetype.name,
            "saksbehandlerIdent" to løsning["Godkjenning"]["saksbehandlerIdent"].asText(),
            "saksbehandlerEpost" to løsning["Godkjenning"]["saksbehandlerEpost"].asText(),
            "automatiskBehandling" to løsning["Godkjenning"]["automatiskBehandling"].asBoolean()
        ))}).toJson()
))*/
