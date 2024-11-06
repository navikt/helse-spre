package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class InntektsmeldingRiverTest {
    private lateinit var dataSource: TestDataSource
    private lateinit var oppgaveDAO: OppgaveDAO
    private val testRapid = TestRapid()
    private val observer = object : Oppgave.Observer {}
    private val publisist = Publisist { _, _ -> }

    @BeforeEach
    fun reset() {
        dataSource = databaseContainer.nyTilkobling()
        oppgaveDAO = OppgaveDAO(dataSource.ds)
        InntektsmeldingRiver(testRapid, oppgaveDAO, publisist)
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
    }

    @Test
    fun `dytter inntektsmelding inn i db`() {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        testRapid.sendTestMessage(inntektsmelding(hendelseId, dokumentId))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        Assertions.assertNotNull(oppgave)
        Assertions.assertEquals(DokumentType.Inntektsmelding, oppgave!!.dokumentType)
    }
}

fun inntektsmelding(
    hendelseId: UUID,
    dokumentId: UUID,
    inntekt: Double = 30000.00,
    refusjon: Double? = inntekt,
    fødselsnummer: String = "12345678910",
    organisasjonsnummer: String = "ORGNUMMER"
) = """{
            "@event_name": "inntektsmelding",
            "@id": "$hendelseId",
            "inntektsmeldingId": "$dokumentId",
            "arbeidstakerFnr": "$fødselsnummer",
            "virksomhetsnummer": "$organisasjonsnummer",
            "beregnetInntekt": "$inntekt"
            ${if (refusjon != null) """,
            "refusjon": {
                "beloepPrMnd": "$refusjon"
            }""" else ""}
        }"""

