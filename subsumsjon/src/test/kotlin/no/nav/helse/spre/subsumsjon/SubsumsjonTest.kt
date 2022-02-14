package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon.SubsumsjonRiver
import no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SubsumsjonTest {

    private val testRapid = TestRapid()

    @Test
    fun `En subsumsjon blir publisert`() {
        val result = mutableListOf<Pair<String, String>>()

        SubsumsjonRiver(rapidsConnection = testRapid) { key, value -> result.add(Pair(key, value)) }

        testRapid.sendTestMessage(testMessage)

        assertEquals(result[0].first, "02126721911")
    }

    @Disabled("Skru denne på igjen når subsumsjon er i produksjon")
    @Test
    fun `En dårlig subsumsjon resulterer i exception`() {
        val result = mutableListOf<Pair<String, String>>()

        SubsumsjonRiver(rapidsConnection = testRapid) { key, value -> result.add(Pair(key, value)) }

        assertThrows(IllegalArgumentException::class.java) { testRapid.sendTestMessage(badTestMessage) }
    }

    @Test
    fun `schema validation`() {
        val result = mutableListOf<Pair<String, String>>()
        SubsumsjonRiver(rapidsConnection = testRapid) { key, value -> result.add(Pair(key, value)) }
        testRapid.sendTestMessage(testMessage)
        assertSubsumsjonsmelding(objectMapper.readTree(result[0].second))
    }

    @Language("JSON")
    private val testMessage = """
  {
    "@id": "1fe967d5-950d-4b52-9f76-59f1f3982a86",
    "@event_name": "subsumsjon",
    "@opprettet": "2022-02-02T14:47:01.326499238",
    "subsumsjon": {
      "tidsstempel": "2022-02-02T14:47:01.326499238",
      "versjon": "1.0.0",
      "kilde": "spleis",
      "versjonAvKode": "docker.pkg.github.com/navikt/helse-spleis/spleis:47404a1",
      "fodselsnummer": "02126721911",
      "sporing": {
        "organisasjonsnummer": ["972674818"],
        "vedtaksperiode": ["7b7ae5bd-a5f5-4c25-996a-1afd7c403b6a"]
      },
      "lovverk": "folketrygdloven",
      "lovverksversjon": "2018-01-01",
      "paragraf": "8-17",
      "input": {
        "arbeidsgiverperioder": [
          {
            "fom": "2021-08-01",
            "tom": "2021-08-16"
          }
        ]
      },
      "output": {
        "førsteUtbetalingsdag": "2021-08-17"
      },
      "utfall": "VILKAR_BEREGNET",
      "ledd": 1,
      "bokstav": "a"
    },
    "system_read_count": 0,
    "system_participating_services": [
      {
        "service": "spleis",
        "instance": "spleis-86b77d6b48-xbfwr",
        "time": "2022-02-02T14:47:01.328378699"
      }
    ]
  }
""".trimIndent()

    @Language("JSON")
    private val badTestMessage = """
    {
      "@id": "1fe967d5-950d-4b52-9f76-59f1f3982a86",
      "@event_name": "subsumsjon",
      "@opprettet": "2022-02-02T14:47:01.326499238",
      "versjon": "1.0.0",
      "kilde": "spleis",
      "versjonavKode": "docker.pkg.github.com/navikt/helse-spleis/spleis:47404a1",
      "fodselsnummer": "02126721911",
      "sporing": {
        "fødselsnummer": "02126721911",
        "organisasjonsnummer": "972674818",
        "vedtaksperiode": "7b7ae5bd-a5f5-4c25-996a-1afd7c403b6a"
      },
      "lovverk": "folketrygdloven",
      "lovverkVersjon": "2018-01-01",
      "paragraf": "8-17",
      "input": {
        "arbeidsgiverperioder": [
          {
            "fom": "2021-08-01",
            "tom": "2021-08-16"
          }
        ]
      },
      "output": {
        "førsteUtbetalingsdag": "2021-08-17"
      },
      "utfall": "VILKAR_BEREGNET",
      "ledd": 1,
      "bokstav": "a",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "service": "spleis",
          "instance": "spleis-86b77d6b48-xbfwr",
          "time": "2022-02-02T14:47:01.328378699"
        }
      ]
    }
""".trimIndent()


    private val schema by lazy {
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(URI("https://raw.githubusercontent.com/navikt/helse/c53bc453251b7878135f31d5d1070e5406ae4af1/subsumsjon/json-schema-1.0.0.json"))
    }

    private fun assertSubsumsjonsmelding(melding: JsonNode) {
        try {
            assertEquals(emptySet<ValidationMessage>(), schema.validate(melding))
        } catch (_: Exception) {
            LoggerFactory.getLogger(SubsumsjonTest::class.java)
                .warn("Kunne ikke kjøre kontrakttest for subsumsjoner. Mangler du internett?")
        }
    }
}