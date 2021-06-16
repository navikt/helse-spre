package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDateTime
import java.util.*

data class SøknadData(
    val hendelseId: UUID,
    val søknadId: UUID,
    val hendelseOpprettet: LocalDateTime
) {
    val asSøknad
        get() =
            Søknad(
                søknadHendelseId = hendelseId,
                søknadDokumentId = søknadId,
                rapportert = hendelseOpprettet,
                registrertDato = hendelseOpprettet,
            )

    companion object {
        fun fromJson(packet: JsonMessage) = SøknadData(
            hendelseId = UUID.fromString(packet["@id"].textValue()),
            søknadId = UUID.fromString(packet["id"].textValue()),
            hendelseOpprettet = packet["@opprettet"].asLocalDateTime(),
        )
    }
}

