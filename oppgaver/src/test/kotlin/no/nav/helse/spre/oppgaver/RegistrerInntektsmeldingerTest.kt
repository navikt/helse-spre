package no.nav.helse.spre.oppgaver

import java.util.*
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrerInntektsmeldingerTest {
    private val dataSource = setupDataSourceMedFlyway()
    private val testRapid = TestRapid()
    private val oppgaveDAO = OppgaveDAO(dataSource)

    init {
        RegistrerInntektsmeldinger(testRapid, oppgaveDAO)
    }

    @Test
    fun `dytter inntektsmelding inn i db`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        testRapid.sendTestMessage(inntektsmelding(hendelseId, dokumentId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId)
        Assertions.assertNotNull(oppgave)
        Assertions.assertEquals(DokumentType.Inntektsmelding, oppgave!!.dokumentType)
    }
}

fun inntektsmelding(
    hendelseId: UUID,
    dokumentId: UUID,
    inntekt: Int = 30000,
    refusjon: Int? = inntekt,
) = """{
            "@event_name": "inntektsmelding",
            "@id": "$hendelseId",
            "inntektsmeldingId": "$dokumentId",
            "beregnetInntekt": "$inntekt"
            ${if (refusjon != null) """,
            "refusjon": {
                "beloepPrMnd": "$refusjon"
            }""" else ""}
        }"""

