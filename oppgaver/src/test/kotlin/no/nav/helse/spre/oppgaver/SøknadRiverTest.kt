package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class SøknadRiverTest {
    private lateinit var dataSource: TestDataSource
    private lateinit var oppgaveDAO: OppgaveDAO
    private val testRapid = TestRapid()
    private val observer = object : Oppgave.Observer {}
    private val publisist = Publisist { _, _ -> }

    @BeforeEach
    fun reset() {
        dataSource = databaseContainer.nyTilkobling()
        oppgaveDAO = OppgaveDAO(dataSource.ds)
        SøknadRiver(testRapid, oppgaveDAO, publisist)
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
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

