package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon.SykemeldingRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SykemeldingRiverTest {

    private lateinit var postgres: PostgreSQLContainer<Nothing>
    private lateinit var mappingDao: MappingDao
    private lateinit var river: SykemeldingRiver
    private val testRapid = TestRapid()


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
            ).getMigratedDataSource()
        )

        river = SykemeldingRiver(testRapid, mappingDao)

    }

    @BeforeEach
    fun before() {
        testRapid.reset()
        resetMappingDb(postgres)
    }

    @Test
    fun `les inn sykmelding`() {
        testRapid.sendTestMessage(testSykemelding)
        assertEquals(
            UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707"),
            mappingDao.hent(UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56"))
        )
    }
}

@Language("JSON")
val testSykemelding = """
    {
      "id": "5995d335-16f9-39b3-a50d-aa744a6af27c",
      "type": "ARBEIDSTAKERE",
      "status": "NY",
      "fnr": "24068715888",
      "sykmeldingId": "6f0a0911-fc3f-4a55-8fb7-8222388b1707",
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
      "@id": "c844bc55-6be7-4987-9116-a0b7cb95ad56",
      "@opprettet": "2022-02-14T09:24:11.428837001"
    }
""".trimIndent()

