package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.kotest.matchers.collections.shouldBeIn
import java.net.URI
import java.util.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory

internal class SubsumsjonTest {
    private val testRapid = TestRapid()
    private val resultater = mutableListOf<Pair<String, JsonNode>>()

    @BeforeEach
    fun before() {
        SubsumsjonV1_0_0River(testRapid) { fnr, melding ->
            resultater.add(fnr to objectMapper.readTree(melding))
        }
        SubsumsjonV1_1_0River(testRapid) { fnr, melding ->
            resultater.add(fnr to objectMapper.readTree(melding))
        }
        SubsumsjonUkjentVersjonRiver(testRapid)
    }

    @Test
    fun `v1_1_0 - En subsumsjon blir publisert`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        testRapid.sendTestMessage(testSubsumsjonV1_1_0(vedtaksperiodeId, behandlingId))

        assertEquals("02126721911", resultater.last().first)
        val subsumsjonMelding = resultater.last().second
        vedtaksperiodeId shouldBeIn subsumsjonMelding.node("sporing.vedtaksperiode").toUUIDs()
        "947064649" shouldBeIn subsumsjonMelding.node("sporing.organisasjonsnummer").map { it.asText() }
        assertEquals(vedtaksperiodeId, subsumsjonMelding.node("vedtaksperiodeId").toUUID())
        assertEquals(behandlingId, subsumsjonMelding.node("behandlingId").toUUID())
    }

    @Test
    fun `En dårlig subsumsjon resulterer i exception`() {
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(badTestMessage) }
    }

    @Test
    fun `En ukjent versjon resulterer i exception`() {
        assertThrows<IllegalStateException> { testRapid.sendTestMessage(testSubsumsjon(versjon = "2.0.0")) }
    }

    @Test
    fun `schema validation`() {
        testRapid.sendTestMessage(testSubsumsjon())
        assertSubsumsjonsmelding(resultater.last().second)
    }

    private fun JsonNode.node(path: String): JsonNode {
        if (!path.contains(".")) return this.path(path)
        return path.split(".").fold(this) { result, key ->
            result.path(key)
        }
    }

    @Language("JSON")
    private fun testSubsumsjon(versjon: String = "1.0.0") = """
  {
    "@id": "1fe967d5-950d-4b52-9f76-59f1f3982a86",
    "@event_name": "subsumsjon",
    "@opprettet": "2022-02-02T14:47:01.326499238",
    "subsumsjon": {
      "tidsstempel": "2022-02-02T14:47:01.326499238+01:00",
      "versjon": "$versjon",
      "kilde": "spleis",
      "versjonAvKode": "docker.pkg.github.com/navikt/helse-spleis/spleis:47404a1",
      "fodselsnummer": "02126721911",
      "sporing": {
        "vedtaksperiode": ["8fe5da85-d00b-4570-afaa-3a3e9403240e"],
        "organisasjonsnummer":["947064649"]
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
    private fun testSubsumsjonV1_1_0(
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        behandlingId: UUID = UUID.randomUUID()
    ) = """
  {
    "@id": "1fe967d5-950d-4b52-9f76-59f1f3982a86",
    "@event_name": "subsumsjon",
    "@opprettet": "2022-02-02T14:47:01.326499238",
    "subsumsjon": {
      "tidsstempel": "2022-02-02T14:47:01.326499238+01:00",
      "versjon": "1.1.0",
      "kilde": "spleis",
      "versjonAvKode": "docker.pkg.github.com/navikt/helse-spleis/spleis:47404a1",
      "fodselsnummer": "02126721911",
      "vedtaksperiodeId": "$vedtaksperiodeId",
      "behandlingId": "$behandlingId",
      "sporing": {
        "vedtaksperiode": ["$vedtaksperiodeId"],
        "organisasjonsnummer":["947064649"]
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
