package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SykemeldingRiverTest {

    private lateinit var postgres: PostgreSQLContainer<Nothing>
    private lateinit var mappingDao: MappingDao
    private lateinit var river: SykemeldingRiver
    private val testRapid = TestRapid()
    private val idValidation = IdValidation(listOf("someNotValidUUID"))


    @BeforeAll
    fun setup() {
        postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withLabel("app-navn", "spre-subsumsjon")
            withReuse(true)
            start()
        }

        val dataSourceBuilder = DataSourceBuilder(postgres.jdbcUrl, postgres.username, postgres.password)
        dataSourceBuilder.migrate()

        mappingDao = MappingDao(dataSourceBuilder.datasource())

        river = SykemeldingRiver(testRapid, mappingDao, idValidation)

    }

    @BeforeEach
    fun before() {
        testRapid.reset()
        resetMappingDb(postgres)
    }

    @Test
    fun `lager mapping på sykmelding`() {
        testRapid.sendTestMessage(testSykmelding)
        assertEquals(
            UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707"),
            mappingDao.hentSykmeldingId(UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56"))
        )
    }

    @Test
    fun `ignorerer poison pills`() {
        testRapid.sendTestMessage(sykmeldingMedPoisonousId)
        assertEquals(
            null,
            mappingDao.hentSykmeldingId(UUID.fromString("c844bc55-6be7-4987-9116-a0b7cb95ad56"))
        )
    }

    @Test
    fun `kræsj på feilaktige sykmeldinger`() {
        assertThrows<IllegalArgumentException> {  testRapid.sendTestMessage(badTestSykmelding) }
    }
}

// Mangler required fields
@Language("JSON")
private val badTestSykmelding = """
    {
      "@event_name": "ny_søknad",
      "@id": "c844bc55-6be7-4987-9116-a0b7cb95ad56",
      "@opprettet": "2022-02-14T09:24:11.428837001"
    }
""".trimIndent()

@Language("JSON")
private val testSykmelding = """
    {
      "id": "5995d335-16f9-39b3-a50d-aa744a6af27c",
      "sykmeldingId": "6f0a0911-fc3f-4a55-8fb7-8222388b1707",
      "@event_name": "ny_søknad",
      "@id": "c844bc55-6be7-4987-9116-a0b7cb95ad56",
      "@opprettet": "2022-02-14T09:24:11.428837001"
    }
""".trimIndent()

@Language("JSON")
private val sykmeldingMedPoisonousId = """
    {
      "id": "5995d335-16f9-39b3-a50d-aa744a6af27c",
      "sykmeldingId": "someNotValidUUID",
      "@event_name": "ny_søknad",
      "@id": "c844bc55-6be7-4987-9116-a0b7cb95ad56",
      "@opprettet": "2022-02-14T09:24:11.428837001"
    }
""".trimIndent()

