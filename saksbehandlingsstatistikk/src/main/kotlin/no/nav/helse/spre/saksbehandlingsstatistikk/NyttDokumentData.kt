package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDateTime
import java.util.*

data class NyttDokumentData(
    val hendelseId: UUID,
    val søknadId: UUID,
    val mottattDato: LocalDateTime,
    val registrertDato: LocalDateTime
) {
    val asSøknad
        get() =
            Søknad(hendelseId, søknadId, mottattDato, registrertDato)

    companion object {
        fun fromJson(packet: JsonMessage) = NyttDokumentData(
            hendelseId = UUID.fromString(packet["@id"].textValue()),
            søknadId = UUID.fromString(packet["id"].textValue()),
            mottattDato = packet["sendtNav"].asLocalDateTime(),
            registrertDato = packet["rapportertDato"].asLocalDateTime(),
        )
    }
}

