package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class NyttDokumentRiverTest {
    private val testRapid = TestRapid()
    private val dataSource = TestUtil.dataSource
    private val søknadDao = SøknadDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, søknadDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `lagrer søknad til basen`() {
        val søknadHendelseId = UUID.randomUUID()
        val søknad = Søknad(søknadHendelseId, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(), null)

        testRapid.sendTestMessage(sendtSøknadNavMessage(søknad))

        val søknadDokumentId = finnSøknadDokumentId(søknadHendelseId)
        assertEquals(søknad.dokumentId, søknadDokumentId)
    }

    @Test
    fun `håndterer duplikate dokumenter`() {
        val søknadHendelseId = UUID.randomUUID()
        val søknad = Søknad(søknadHendelseId, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now(), null)

        testRapid.sendTestMessage(sendtSøknadArbeidsgiverMessage(søknad))
        testRapid.sendTestMessage(sendtSøknadNavMessage(søknad))

        val søknadDokumentId = finnSøknadDokumentId(søknadHendelseId)
        assertEquals(søknad.dokumentId, søknadDokumentId)
    }

    private fun finnSøknadDokumentId(søknadHendelseId: UUID) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM søknad WHERE hendelse_id = ?::uuid"
        session.run(
            queryOf(query, søknadHendelseId)
                .map { row -> UUID.fromString(row.string("dokument_id"))}.asSingle
        )
    }

    private fun sendtSøknadNavMessage(søknad: Søknad) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sendtNav": "2021-01-01T00:00:00",
            "rapportertDato": "2021-01-01T00:00:00"
        }"""

    private fun sendtSøknadArbeidsgiverMessage(søknad: Søknad) =
        """{
            "@event_name": "sendt_søknad_arbeidsgiver",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sendtNav": "2021-01-01T00:00:00",
            "rapportertDato": "2021-01-01T00:00:00"
        }"""
}
