package no.nav.helse.spre.sykmeldt

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*

class SkatteinntekterLagtTilGrunnRiverTest {

    private val testRapid = TestRapid()
    private val publisher = TestForelagteOpplysningerPublisher()
    private lateinit var river: SkatteinntekterLagtTilGrunnRiver

    @BeforeEach
    fun setup() {
        river = SkatteinntekterLagtTilGrunnRiver(testRapid, publisher)
    }

    @Test
    fun `Kan lese melding om at skatteinntekter har blitt lagt til grunn, og sender den videre`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val message = skatteinntekterLagtTilGrunnEvent(vedtaksperiodeId)
        testRapid.sendTestMessage(message)

        assertTrue(publisher.harSendtMelding(vedtaksperiodeId))
    }

    @Test
    fun `Dataelementer som vi sender videre`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val message = skatteinntekterLagtTilGrunnEvent(vedtaksperiodeId)
        testRapid.sendTestMessage(message)

        assertEquals(1, publisher.sendteMeldinger.size)
        val sendtMelding = publisher.sendteMeldinger.single()
        val datafelter = listOf("skatteinntekter", "vedtaksperiodeId", "behandlingId", "tidsstempel", "omregnetÅrsinntekt")
        assertDoesNotThrow { datafelter.forEach { sendtMelding.javaClass.getDeclaredField(it) } }
    }

    @Language("JSON")
    private fun skatteinntekterLagtTilGrunnEvent(vedtaksperiodeId: UUID): String {
        return """
       {
         "@event_name": "skatteinntekter_lagt_til_grunn",
         "organisasjonsnummer": "987654321",
         "vedtaksperiodeId": "$vedtaksperiodeId",
         "behandlingId": "264ef682-d276-48d0-9f41-2a9dac711175",
         "omregnetÅrsinntekt": 372000.0,
         "skatteinntekter": [
           {
             "måned": "2017-10",
             "beløp": 31000.0
           },
           {
             "måned": "2017-11",
             "beløp": 31000.0
           },
           {
             "måned": "2017-12",
             "beløp": 31000.0
           }
         ],
         "@id": "dfa2ebc9-1ee8-4dc1-9f2e-e63d0b96fb5b",
         "@opprettet": "2024-09-27T13:07:25.898735",
         "aktørId": "42",
         "fødselsnummer": "12029240045"
       }
       """
    }
}