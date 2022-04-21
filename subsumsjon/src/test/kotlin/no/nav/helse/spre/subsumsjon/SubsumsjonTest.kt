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
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SubsumsjonTest {
    private val testRapid = TestRapid()
    private val resultater = mutableListOf<Pair<String, JsonNode>>()
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
        SubsumsjonRiver(testRapid, mappingDao) { fnr, melding ->
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

        testRapid.sendTestMessage(
            testSykmelding(
                hendelseId = sykmeldingId,
                dokumentId = sykmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(
            testSykmelding(
                hendelseId = sykmeldingId, // sjekk dokumentId ikke blir overskrevet av ny hendelse
                dokumentId = sykemeldingDuplikatDokumentId
            )
        )
        testRapid.sendTestMessage(
            testSykmelding(
                hendelseId = UUID.randomUUID(),
                dokumentId = sykmeldingDokumentId
            )
        )
        testRapid.sendTestMessage(testSøknad(
            hendelseId = søknadId,
            dokumentId = søknadDokumentId,
            sykmeldingDokumentId = sykmeldingDokumentId
        ))
        testRapid.sendTestMessage(testSøknad(
            hendelseId = søknadId,
            dokumentId = søknadDuplikatDokumentId,
            sykmeldingDokumentId = sykmeldingDokumentId
        ))
        testRapid.sendTestMessage(testInntektsmelding(
            hendelseId = inntektsmeldingId,
            inntektsmeldingId = inntektsmeldingDokumentId
        ))
        testRapid.sendTestMessage(testInntektsmelding(
            hendelseId = UUID.randomUUID(),
            inntektsmeldingId = inntektsmeldingDuplikatDokumentId
        )) // sjekk at vi kan håndtere flere av samme
        testRapid.sendTestMessage(testInntektsmelding(
            hendelseId = inntektsmeldingId,
            inntektsmeldingId = inntektsmeldingDuplikatDokumentId
        )) // sjekk at vi håndterer duplikater

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

        testRapid.sendTestMessage(testSøknad(
            hendelseId = søknadId,
            dokumentId = søknadDokumentId,
            sykmeldingDokumentId = sykmeldingDokumentId
        ))
        testRapid.sendTestMessage(testInntektsmelding(
            hendelseId = inntektsmeldingId,
            inntektsmeldingId = inntektsmeldingDokumentId
        ))
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

        mappingDao.lagre(
            hendelseId = sykmeldingId,
            dokumentId = sykmeldingDokumentId,
            dokumentIdType = DokumentIdType.Sykmelding,
            hendelseNavn = "ny_søknad",
            produsert = LocalDateTime.now()
        )
        mappingDao.lagre(
            hendelseId = søknadId,
            dokumentId = søknadDokumentId,
            dokumentIdType = DokumentIdType.Søknad,
            hendelseNavn = "sendt_søknad_nav",
            produsert = LocalDateTime.now()
        )
        mappingDao.lagre(
            hendelseId = inntektsmeldingId,
            dokumentId = inntektsmeldingDokumentId,
            dokumentIdType = DokumentIdType.Inntektsmelding,
            hendelseNavn = "inntektsmelding",
            produsert = LocalDateTime.now()
        )

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
      "tidsstempel": "2022-02-02T14:47:01.326499238",
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


@Language("JSON")
private fun testSykmelding(hendelseId: UUID, dokumentId: UUID) = """
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
private fun testInntektsmelding(hendelseId: UUID, inntektsmeldingId: UUID) = """
    {
      "@event_name": "inntektsmelding",
      "@id": "$hendelseId",
      "@opprettet": "2022-02-15T08:36:35.048196790",
      "inntektsmeldingId": "$inntektsmeldingId",
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
private fun testSøknad(hendelseId: UUID, dokumentId: UUID, sykmeldingDokumentId: UUID)= """
    {
      "@event_name": "sendt_søknad_nav",
      "@id": "$hendelseId",
      "@opprettet": "2022-02-15T08:36:34.842818327",
      "id": "$dokumentId",
      "fnr": "24068715888",
      "type": "ARBEIDSTAKERE",
      "status": "SENDT",
      "aktorId": "2012213570475",
      "sykmeldingId": "$sykmeldingDokumentId",
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