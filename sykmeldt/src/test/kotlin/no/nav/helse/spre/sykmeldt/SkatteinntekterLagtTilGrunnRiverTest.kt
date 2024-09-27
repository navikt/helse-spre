package no.nav.helse.spre.sykmeldt

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkatteinntekterLagtTilGrunnRiverTest {

    private val testRapid = TestRapid()
    private lateinit var river: SkatteinntekterLagtTilGrunnRiver

    @BeforeEach
    fun setup() {
        river = SkatteinntekterLagtTilGrunnRiver(testRapid)
    }

    @Test
    fun `Kan lese melding om at skatteinntekter har blitt lagt til grunn`() {
        assertFalse(river.lestMelding)

        val message = skatteinntekterLagtTilGrunnEvent()
        testRapid.sendTestMessage(message)

        assertTrue(river.lestMelding)
    }

    @Language("JSON")
    private fun skatteinntekterLagtTilGrunnEvent(): String {
        return """
       {
         "@event_name": "skatteinntekter_lagt_til_grunn",
         "organisasjonsnummer": "987654321",
         "vedtaksperiodeId": "96001cdf-e6fb-4ed5-8f69-9b9386e86fba",
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