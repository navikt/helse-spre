package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

    @ParameterizedTest
    @ValueSource(strings = ["inntektsmelding", "arbeidsgiveropplysninger", "korrigerte_arbeidsgiveropplysninger", "selvbestemte_arbeidsgiveropplysninger"])
    fun `dytter inntektsmelding og arbeidsgiveropplysninger inn i db`(eventName: String) {
        val hendelseId = UUID.randomUUID()
        val dokumentId = UUID.randomUUID()
        testRapid.sendTestMessage(inntektsmelding(hendelseId, dokumentId, eventName))

        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer)
        assertNotNull(oppgave)
        assertEquals(DokumentType.Inntektsmelding, oppgave!!.dokumentType)
    }
}

fun inntektsmelding(
    hendelseId: UUID,
    dokumentId: UUID,
    eventName: String,
    inntekt: Double = 30000.00,
    refusjon: Double? = inntekt,
    fødselsnummer: String = "12345678910",
    organisasjonsnummer: String = "ORGNUMMER"
) = """{
            "@event_name": "$eventName",
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

