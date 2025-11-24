package no.nav.helse.spre.gosys.vedtakFattet

import java.time.Instant
import java.util.UUID

class MeldingOmVedtak(
    val id: UUID,
    val utbetalingId: UUID?,
    val fødselsnummer: String,
    journalførtTidspunkt: Instant?,
    val json: String,
) {
    var journalførtTidspunkt: Instant? = journalførtTidspunkt
        private set

    fun erJournalført(): Boolean {
        val journalførtTidspunkt = this.journalførtTidspunkt
        return journalførtTidspunkt != null && journalførtTidspunkt > NITTEN_ÅTTI
    }

    fun journalfør() {
        this.journalførtTidspunkt = Instant.now()
    }

    private companion object {
        val NITTEN_ÅTTI: Instant = Instant.parse("1980-01-01T00:00:00Z")
    }
}
