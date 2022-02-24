package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeIn
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.net.URI
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SubsumsjonTest {

    private val testRapid = TestRapid()
    private lateinit var postgres: PostgreSQLContainer<Nothing>
    private lateinit var mappingDao: MappingDao
    private lateinit var sykemeldingRiver: SykemeldingRiver
    private lateinit var søknadRiver: SøknadRiver
    private lateinit var inntektsmeldingRiver: InntektsmeldingRiver

    @BeforeAll
    fun setup() {
        postgres = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withLabel("app-navn", "spre-subsumsjon")
            withReuse(true)
            start()
        }

        mappingDao = MappingDao(
            DataSourceBuilder(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password
            ).migratedDataSource()
        )

        sykemeldingRiver = SykemeldingRiver(testRapid, mappingDao, IdValidation(emptyList()))
        søknadRiver = SøknadRiver(testRapid, mappingDao)
        inntektsmeldingRiver = InntektsmeldingRiver(testRapid, mappingDao)
    }

    @Test
    fun `En subsumsjon blir publisert`() {
        val result = mutableListOf<Pair<String, String>>()

        SubsumsjonRiver(rapidsConnection = testRapid, mappingDao = mappingDao) { key, value ->
            result.add(
                Pair(
                    key,
                    value
                )
            )
        }


        testRapid.sendTestMessage(
            testSykemelding(
                hendelseId = UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56"),
                dokumentId = UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707")
            )
        )
        testRapid.sendTestMessage(
            testSykemelding(
                hendelseId = UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56"), // sjekk ny @id ikke endrer ekisterene mapping
                dokumentId = UUID.fromString("adc52721-f9a2-40ae-bf1b-6945cf19c790")
            )
        )
        testRapid.sendTestMessage(
            testSykemelding(
                hendelseId = UUID.fromString("6dad49e1-099b-44aa-a6f3-7132cdacd6aa"),
                dokumentId = UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707")
            )
        )
        testRapid.sendTestMessage(testSøknad("59fbfbee-1e7d-4b60-9604-20f77ee62d0f", "be4586ce-d45e-419b-8271-1bc2be839e16"))
        testRapid.sendTestMessage(testInntektsmelding(UUID.fromString("b3b2a306-7baa-4916-899f-28c2ef2ca9e9")))
        testRapid.sendTestMessage(testInntektsmelding(UUID.fromString("b211d477-254d-4dd1-bd16-cdbcc8554f01"))) // sjekk at vi kan håndtere flere av samme
        testRapid.sendTestMessage(testInntektsmelding(UUID.fromString("b3b2a306-7baa-4916-899f-28c2ef2ca9e9"))) // sjekk at vi håndterer duplikater

        testRapid.sendTestMessage(testSubsumsjon)

        assertEquals("02126721911", result[0].first)
        val subsumsjonMelding = objectMapper.readTree(result[0].second)
        UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707") shouldBeIn subsumsjonMelding.node("sporing.sykmelding")
            .toUUIDs()
        UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56") shouldNotBeIn  subsumsjonMelding.node("sporing.sykmelding")
            .toUUIDs()

        UUID.fromString("be4586ce-d45e-419b-8271-1bc2be839e16") shouldBeIn subsumsjonMelding.node("sporing.soknad")
            .toUUIDs()
        UUID.fromString("59fbfbee-1e7d-4b60-9604-20f77ee62d0f") shouldNotBeIn subsumsjonMelding.node("sporing.soknad")
            .toUUIDs()

        UUID.fromString("85a30422-b6ca-4adf-8776-78afb68cb903") shouldBeIn subsumsjonMelding.node("sporing.inntektsmelding")
            .toUUIDs()
        UUID.fromString("b3b2a306-7baa-4916-899f-28c2ef2ca9e9") shouldNotBeIn subsumsjonMelding.node("sporing.inntektsmelding")
            .toUUIDs()
    }


    @Test
    fun `En dårlig subsumsjon resulterer i exception`() {
        val result = mutableListOf<Pair<String, String>>()

        SubsumsjonRiver(rapidsConnection = testRapid, mappingDao = mappingDao) { key, value ->
            result.add(
                Pair(
                    key,
                    value
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) { testRapid.sendTestMessage(badTestMessage) }
    }

    @Test
    fun `schema validation`() {
        val result = mutableListOf<Pair<String, String>>()
        SubsumsjonRiver(rapidsConnection = testRapid, mappingDao = mappingDao) { key, value ->
            result.add(
                Pair(
                    key,
                    value
                )
            )
        }
        testRapid.sendTestMessage(testSubsumsjon)
        assertSubsumsjonsmelding(objectMapper.readTree(result[0].second))
    }

    private fun JsonNode.node(path: String): JsonNode {
        if (!path.contains(".")) return this.path(path)
        return path.split(".").fold(this) { result, key ->
            result.path(key)
        }
    }

    @Language("JSON")
    private val testSubsumsjon = """
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
        "vedtaksperiode": ["8fe5da85-d00b-4570-afaa-3a3e9403240e"],
        "sykmelding": [ "c844bc55-6be7-4987-9116-a0b7cb95ad56"],
        "soknad":["59fbfbee-1e7d-4b60-9604-20f77ee62d0f"],
        "inntektsmelding":["b3b2a306-7baa-4916-899f-28c2ef2ca9e9", "b211d477-254d-4dd1-bd16-cdbcc8554f01"],
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


@Language("JSON")
private fun testSykemelding(hendelseId: UUID, dokumentId: UUID) = """
    {
      "id": "5995d335-16f9-39b3-a50d-aa744a6af27c",
      "type": "ARBEIDSTAKERE",
      "status": "NY",
      "fnr": "24068715888",
      "sykmeldingId": "$dokumentId",
      "arbeidsgiver": {
        "navn": "SJOKKERENDE ELEKTRIKER",
        "orgnummer": "947064649"
      },
      "arbeidssituasjon": "ARBEIDSTAKER",
      "korrigerer": null,
      "korrigertAv": null,
      "soktUtenlandsopphold": null,
      "arbeidsgiverForskutterer": null,
      "fom": "2022-01-01",
      "tom": "2022-01-31",
      "dodsdato": null,
      "startSyketilfelle": "2022-01-01",
      "arbeidGjenopptatt": null,
      "sykmeldingSkrevet": "2022-01-01T00:00:00",
      "opprettet": "2022-02-14T09:24:11.428837001",
      "sendtNav": null,
      "sendtArbeidsgiver": null,
      "egenmeldinger": [],
      "fravarForSykmeldingen": [],
      "papirsykmeldinger": [],
      "fravar": [],
      "andreInntektskilder": [],
      "soknadsperioder": [
        {
          "fom": "2022-01-01",
          "tom": "2022-01-31",
          "sykmeldingsgrad": 100,
          "faktiskGrad": null,
          "avtaltTimer": null,
          "faktiskTimer": null,
          "sykmeldingstype": "AKTIVITET_IKKE_MULIG",
          "grad": 100
        }
      ],
      "sporsmal": [],
      "avsendertype": null,
      "ettersending": false,
      "mottaker": null,
      "egenmeldtSykmelding": false,
      "harRedusertVenteperiode": null,
      "behandlingsdager": null,
      "permitteringer": [],
      "merknaderFraSykmelding": null,
      "system_read_count": 1,
      "system_participating_services": [
        {
          "service": "spedisjon",
          "instance": "spedisjon-74f9d56766-997vn",
          "time": "2022-02-14T09:24:12.117944652"
        },
        {
          "service": "spleis",
          "instance": "spleis-75f7c8c58c-ct9pk",
          "time": "2022-02-14T09:24:12.335203226"
        }
      ],
      "aktorId": "2012213570475",
      "@event_name": "ny_søknad",
      "@id": "$hendelseId",
      "@opprettet": "2022-02-14T09:24:11.428837001"
    }
""".trimIndent()


@Language("JSON")
private fun testInntektsmelding(id: UUID) = """
    {
      "@event_name": "inntektsmelding",
      "@id": "$id",
      "@opprettet": "2022-02-15T08:36:35.048196790",
      "inntektsmeldingId": "85a30422-b6ca-4adf-8776-78afb68cb903",
      "arbeidstakerFnr": "24068715888",
      "arbeidstakerAktorId": "2012213570475",
      "virksomhetsnummer": "947064649",
      "arbeidsgiverFnr": "Don't care",
      "arbeidsgiverAktorId": "Don't care",
      "arbeidsgivertype": "VIRKSOMHET",
      "arbeidsforholdId": "",
      "beregnetInntekt": "21000.0",
      "rapportertDato": "2022-01-02",
      "refusjon": {
        "beloepPrMnd": "null",
        "opphoersdato": null
      },
      "endringIRefusjoner": [],
      "opphoerAvNaturalytelser": [],
      "gjenopptakelseNaturalytelser": [],
      "arbeidsgiverperioder": [
        {
          "fom": "2022-01-01",
          "tom": "2022-01-16"
        }
      ],
      "ferieperioder": [],
      "status": "GYLDIG",
      "arkivreferanse": "ENARKIVREFERANSE",
      "hendelseId": "f1db0ff9-92fd-4eb0-b6d8-e68c44421910",
      "foersteFravaersdag": "2022-01-01",
      "mottattDato": "2022-01-01T00:00",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "service": "spleis",
          "instance": "spleis-674dc45bc7-v6t4v",
          "time": "2022-02-15T08:36:35.278183584"
        }
      ]
    }
""".trimIndent()

@Language("JSON")
private fun testSøknad(hendelseId: String, dokumentId: String)= """
    {
      "@event_name": "sendt_søknad_nav",
      "@id": "$hendelseId",
      "@opprettet": "2022-02-15T08:36:34.842818327",
      "id": "$dokumentId",
      "fnr": "24068715888",
      "type": "ARBEIDSTAKERE",
      "status": "SENDT",
      "aktorId": "2012213570475",
      "sykmeldingId": "3a82b571-d73f-4fc8-a0a7-527053004999",
      "arbeidsgiver": {
        "navn": "Nærbutikken AS",
        "orgnummer": "947064649"
      },
      "arbeidssituasjon": "ARBEIDSTAKER",
      "korrigerer": null,
      "korrigertAv": null,
      "soktUtenlandsopphold": null,
      "arbeidsgiverForskutterer": null,
      "fom": "2022-01-01",
      "tom": "2022-01-31",
      "startSyketilfelle": "2022-01-01",
      "arbeidGjenopptatt": null,
      "sykmeldingSkrevet": "2022-01-01T00:00",
      "opprettet": "2022-01-01T00:00",
      "sendtNav": "2022-02-01T00:00",
      "sendtArbeidsgiver": null,
      "egenmeldinger": [],
      "papirsykmeldinger": [],
      "fravar": [],
      "andreInntektskilder": [],
      "soknadsperioder": [
        {
          "fom": "2022-01-01",
          "tom": "2022-01-31",
          "sykmeldingsgrad": 100,
          "faktiskGrad": null,
          "avtaltTimer": null,
          "faktiskTimer": null,
          "sykmeldingstype": null
        }
      ],
      "sporsmal": null,
      "hendelseId": "a7e5cb14-b961-475e-8be3-c648ded77427",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "service": "spleis",
          "instance": "spleis-674dc45bc7-v6t4v",
          "time": "2022-02-15T08:36:35.070794015"
        }
      ]
    }
""".trimIndent()