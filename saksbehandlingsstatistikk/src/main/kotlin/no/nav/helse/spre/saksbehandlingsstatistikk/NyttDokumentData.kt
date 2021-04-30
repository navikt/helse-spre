package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*

data class NyttDokumentData(
    val hendelseId: UUID,
    val søknadId : UUID,
    val mottattDato : LocalDateTime,
    val registrertDato : LocalDateTime
)

