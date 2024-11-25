package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.spedisjon.HentMeldingResponse
import com.github.navikt.tbd_libs.spedisjon.HentMeldingerResponse
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDateTime
import java.util.*

internal class SubsumsjonTest {
    private val testRapid = TestRapid()
    private val resultater = mutableListOf<Pair<String, JsonNode>>()
    private val spedisjonClient = mockk<SpedisjonClient>()

    @BeforeEach
    fun before() {
        every {
            spedisjonClient.hentMeldinger(any(), any())
        } returns HentMeldingerResponse(emptyList()).ok()

        SubsumsjonRiver(testRapid, spedisjonClient) { fnr, melding ->
            resultater.add(fnr to objectMapper.readTree(melding))
        }
    }

    @Test
    fun `En subsumsjon blir publisert`() {
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        val sykmeldingDokumentId = UUID.randomUUID()
        val sykemeldingDuplikatDokumentId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val søknadDuplikatDokumentId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()
        val inntektsmeldingDuplikatDokumentId = UUID.randomUUID()

        clearMocks(spedisjonClient)
        every {
            spedisjonClient.hentMeldinger(any(), any())
        } returns HentMeldingerResponse(listOf(
            HentMeldingResponse(
                type = "ny_søknad",
                fnr = "",
                internDokumentId = sykmeldingId,
                eksternDokumentId = sykmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
            HentMeldingResponse(
                type = "sendt_søknad_nav",
                fnr = "",
                internDokumentId = søknadId,
                eksternDokumentId = søknadDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
            HentMeldingResponse(
                type = "inntektsmelding",
                fnr = "",
                internDokumentId = inntektsmeldingId,
                eksternDokumentId = inntektsmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
        )).ok()

        testRapid.sendTestMessage(testSubsumsjon(
            sykmeldingIder = listOf(sykmeldingId),
            søknadIder = listOf(søknadId),
            inntektsmeldingIder = listOf(inntektsmeldingId)
        ))

        assertEquals("02126721911", resultater.last().first)
        val subsumsjonMelding = resultater.last().second
        sykmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.sykmelding").toUUIDs()
        sykemeldingDuplikatDokumentId shouldNotBeIn subsumsjonMelding.node("sporing.sykmelding").toUUIDs()

        søknadDokumentId shouldBeIn subsumsjonMelding.node("sporing.soknad").toUUIDs()
        søknadDuplikatDokumentId shouldNotBeIn subsumsjonMelding.node("sporing.soknad").toUUIDs()

        inntektsmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.inntektsmelding").toUUIDs()
        inntektsmeldingDuplikatDokumentId shouldNotBeIn subsumsjonMelding.node("sporing.inntektsmelding").toUUIDs()
    }

    @Test
    fun `Subsumsjon uten sykmeldingIder`() {
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        val sykmeldingDokumentId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        clearMocks(spedisjonClient)
        every {
            spedisjonClient.hentMeldinger(any(), any())
        } returns HentMeldingerResponse(listOf(
            HentMeldingResponse(
                type = "sendt_søknad_nav",
                fnr = "",
                internDokumentId = søknadId,
                eksternDokumentId = søknadDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = """{ "sykmeldingId": "$sykmeldingDokumentId" }"""
            ),
            HentMeldingResponse(
                type = "inntektsmelding",
                fnr = "",
                internDokumentId = inntektsmeldingId,
                eksternDokumentId = inntektsmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
        )).ok()

        testRapid.sendTestMessage(testSubsumsjon(
            sykmeldingIder = emptyList(),
            søknadIder = listOf(søknadId),
            inntektsmeldingIder = listOf(inntektsmeldingId)
        ))

        val subsumsjonMelding = resultater.last().second

        sykmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.sykmelding").toUUIDs()
        søknadDokumentId shouldBeIn subsumsjonMelding.node("sporing.soknad").toUUIDs()
        inntektsmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.inntektsmelding").toUUIDs()
    }

    @Test
    fun `godta at vi ikke har sykmeldingId for gamle søknader`() {
        val sykmeldingId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        val sykmeldingDokumentId = UUID.randomUUID()
        val søknadDokumentId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        clearMocks(spedisjonClient)
        every {
            spedisjonClient.hentMeldinger(any(), any())
        } returns HentMeldingerResponse(listOf(
            HentMeldingResponse(
                type = "ny_søknad",
                fnr = "",
                internDokumentId = sykmeldingId,
                eksternDokumentId = sykmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
            HentMeldingResponse(
                type = "sendt_søknad_nav",
                fnr = "",
                internDokumentId = søknadId,
                eksternDokumentId = søknadDokumentId,
                rapportertDato = LocalDateTime.of(2020, 1, 1, 12, 0),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
            HentMeldingResponse(
                type = "inntektsmelding",
                fnr = "",
                internDokumentId = inntektsmeldingId,
                eksternDokumentId = inntektsmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
        )).ok()

        assertDoesNotThrow {
            testRapid.sendTestMessage(
                testSubsumsjon(
                    sykmeldingIder = listOf(sykmeldingId),
                    søknadIder = listOf(søknadId),
                    inntektsmeldingIder = listOf(inntektsmeldingId)
                )
            )
        }
        val subsumsjonMelding = resultater.last().second
        sykmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.sykmelding").toUUIDs()
        søknadDokumentId shouldBeIn subsumsjonMelding.node("sporing.soknad").toUUIDs()
        inntektsmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.inntektsmelding").toUUIDs()
    }

    @Test
    fun `gammel vedtaksperiode som ble opprettet av søknad`() {
        val søknadId = UUID.randomUUID()
        val inntektsmeldingId = UUID.randomUUID()

        val søknadDokumentId = UUID.randomUUID()
        val inntektsmeldingDokumentId = UUID.randomUUID()

        clearMocks(spedisjonClient)
        every {
            spedisjonClient.hentMeldinger(any(), any())
        } returns HentMeldingerResponse(listOf(
            HentMeldingResponse(
                type = "sendt_søknad_nav",
                fnr = "",
                internDokumentId = søknadId,
                eksternDokumentId = søknadDokumentId,
                rapportertDato = LocalDateTime.of(2022, 1, 1, 12, 0),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
            HentMeldingResponse(
                type = "inntektsmelding",
                fnr = "",
                internDokumentId = inntektsmeldingId,
                eksternDokumentId = inntektsmeldingDokumentId,
                rapportertDato = LocalDateTime.now(),
                duplikatkontroll = "unik nøkkel",
                jsonBody = "{}"
            ),
        )).ok()

        assertDoesNotThrow {
            testRapid.sendTestMessage(
                testSubsumsjon(
                    sykmeldingIder = emptyList(),
                    søknadIder = listOf(søknadId),
                    inntektsmeldingIder = listOf(inntektsmeldingId)
                )
            )
        }
        val subsumsjonMelding = resultater.last().second
        assertEquals(emptyList<UUID>(), subsumsjonMelding.node("sporing.sykmelding").toUUIDs())
        søknadDokumentId shouldBeIn subsumsjonMelding.node("sporing.soknad").toUUIDs()
        inntektsmeldingDokumentId shouldBeIn subsumsjonMelding.node("sporing.inntektsmelding").toUUIDs()
    }

    @Test
    fun `En dårlig subsumsjon resulterer i exception`() {

        assertThrows(IllegalArgumentException::class.java) { testRapid.sendTestMessage(badTestMessage) }
    }

    @Test
    fun `schema validation`() {
        testRapid.sendTestMessage(testSubsumsjon(
            sykmeldingIder = emptyList(),
            søknadIder = emptyList(),
            inntektsmeldingIder = emptyList()
        ))
        assertSubsumsjonsmelding(resultater.last().second)
    }

    private fun JsonNode.node(path: String): JsonNode {
        if (!path.contains(".")) return this.path(path)
        return path.split(".").fold(this) { result, key ->
            result.path(key)
        }
    }

    @Language("JSON")
    private fun testSubsumsjon(
        sykmeldingIder: List<UUID>,
        søknadIder: List<UUID>,
        inntektsmeldingIder: List<UUID>
    ) = """
  {
    "@id": "1fe967d5-950d-4b52-9f76-59f1f3982a86",
    "@event_name": "subsumsjon",
    "@opprettet": "2022-02-02T14:47:01.326499238",
    "subsumsjon": {
      "tidsstempel": "2022-02-02T14:47:01.326499238+01:00",
      "versjon": "1.0.0",
      "kilde": "spleis",
      "versjonAvKode": "docker.pkg.github.com/navikt/helse-spleis/spleis:47404a1",
      "fodselsnummer": "02126721911",
      "sporing": {
        "vedtaksperiode": ["8fe5da85-d00b-4570-afaa-3a3e9403240e"],
        "sykmelding": ${objectMapper.writeValueAsString(sykmeldingIder)},
        "soknad":${objectMapper.writeValueAsString(søknadIder)},
        "inntektsmelding": ${objectMapper.writeValueAsString(inntektsmeldingIder)},
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