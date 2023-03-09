package no.nav.helse.spre.oppgaver

import java.util.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrerSøknaderTest {
    private val dataSource = setupDataSourceMedFlyway()
    private val testRapid = TestRapid()
    private val oppgaveDAO = OppgaveDAO(dataSource)
    private val observer = object : Oppgave.Observer {
        override fun forlengTimeout(oppgave: Oppgave, timeout: LocalDateTime) {}
        override fun forlengTimeoutUtenUtbetalingTilSøker(oppgave: Oppgave, timeout: LocalDateTime): Boolean { return true }
    }
    init {
        RegistrerSøknader(testRapid, oppgaveDAO)
    }

    @Test
    fun `dytter søknader inn i db`() {
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(sendtSøknad(hendelseId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        assertNotNull(oppgave)
        assertEquals(DokumentType.Søknad, oppgave!!.dokumentType)
    }
}

fun sendtSøknad(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID(),
    fnr: String = "12345678910",
    orgnummer: String = "ORGNUMMER",
): String =
    """{
            "@event_name": "sendt_søknad_nav",
            "fnr": "$fnr",
            "arbeidsgiver": {
                "navn": "navn",
                "orgnummer": "$orgnummer"
            },
            "@id": "$hendelseId",
            "id": "$dokumentId"
        }"""

fun sendtArbeidsgiversøknad(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID()
): String =
    """{
            "@event_name": "sendt_søknad_arbeidsgiver",
            "@id": "$hendelseId",
            "id": "$dokumentId"
        }"""

