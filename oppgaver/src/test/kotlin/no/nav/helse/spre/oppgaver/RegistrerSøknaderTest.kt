package no.nav.helse.spre.oppgaver

import java.util.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrerSøknaderTest {
    private val dataSource = setupDataSourceMedFlyway()
    private val testRapid = TestRapid()
    private val oppgaveDAO = OppgaveDAO(dataSource)
    private val observer = object : Oppgave.Observer {}
    private val publisist = Publisist { _, _ -> }

    init {
        RegistrerSøknader(testRapid, oppgaveDAO, publisist)
    }

    @Test
    fun `dytter søknader inn i db`() {
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(sendtSøknad(hendelseId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        assertNotNull(oppgave)
        assertEquals(DokumentType.Søknad, oppgave!!.dokumentType)
    }

    @Test
    fun `dytter arbeidsgiversøknader inn i db`() {
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(sendtArbeidsgiversøknad(hendelseId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        assertNotNull(oppgave)
        assertEquals(DokumentType.Søknad, oppgave!!.dokumentType)
    }

    @Test
    fun `dytter arbeidsledigsøknader inn i db`() {
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(sendtSøknadArbeidsledig(hendelseId))

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

fun sendtSøknadArbeidsledig(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID(),
    fnr: String = "12345678910"
): String =
    """{
            "@event_name": "sendt_søknad_arbeidsledig",
            "fnr": "$fnr",
            "@id": "$hendelseId",
            "id": "$dokumentId"
        }"""

fun sendtArbeidsgiversøknad(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID(),
    fnr: String = "12345678910"
): String =
    """{
            "@event_name": "sendt_søknad_arbeidsgiver",
            "fnr": "$fnr",
            "@id": "$hendelseId",
            "id": "$dokumentId"
        }"""

