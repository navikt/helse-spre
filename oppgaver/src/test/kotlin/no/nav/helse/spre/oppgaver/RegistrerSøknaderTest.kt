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
    private val søknadsperioderDAO = SøknadsperioderDAO(dataSource)

    init {
        RegistrerSøknader(testRapid, oppgaveDAO, søknadsperioderDAO)
    }

    @Test
    fun `dytter søknader inn i db`() {
        val hendelseId = UUID.randomUUID()
        testRapid.sendTestMessage(sendtSøknad(hendelseId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId)
        assertNotNull(oppgave)
        assertEquals(DokumentType.Søknad, oppgave!!.dokumentType)
    }
}

fun nySøknad(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID(),
    fom: String,
    tom: String,
): String =
    """{
            "@event_name": "ny_søknad",
            "@id": "$hendelseId",
            "id": "$dokumentId",
            "fom": "$fom",
            "tom": "$tom"
        }"""

fun sendtSøknad(
    hendelseId: UUID,
    dokumentId: UUID = UUID.randomUUID(),
    fom: String = "2021-01-01",
    tom: String = "2021-01-31",
): String =
    """{
            "@event_name": "sendt_søknad_nav",
            "@id": "$hendelseId",
            "id": "$dokumentId",
            "fom": "$fom",
            "tom": "$tom"
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

