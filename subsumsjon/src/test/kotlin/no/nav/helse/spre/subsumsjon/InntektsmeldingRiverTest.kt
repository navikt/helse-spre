package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InntektsmeldingRiverTest {

    private lateinit var postgres: PostgreSQLContainer<Nothing>
    private lateinit var mappingDao: MappingDao
    private lateinit var river: InntektsmeldingRiver
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
            ).migratedDataSource()
        )

        river = InntektsmeldingRiver(testRapid, mappingDao)

    }

    @BeforeEach
    fun before() {
        testRapid.reset()
        resetMappingDb(postgres)
    }

    @Test
    fun `lager mapping på inntektsmelding`() {
        testRapid.sendTestMessage(testInntektsmelding("ebe84db6-049d-49c4-b09e-480a03b1d470"))
        Assertions.assertEquals(
            UUID.fromString("29735b0d-921c-44b3-a4f2-56b3aa1d0e36"),
            mappingDao.hentInntektsmeldingId(UUID.fromString("ebe84db6-049d-49c4-b09e-480a03b1d470"))
        )
    }

    @Test
    fun `kræsj på feilaktige inntektsmelding`() {
        assertThrows<IllegalArgumentException> { testRapid.sendTestMessage(badTestInntektsmelding) }
    }

    // Mangler required fields
    @Language("JSON")
    private val badTestInntektsmelding = """
    {
        "@event_name": "inntektsmelding",
        "@id": "ebe84db6-049d-49c4-b09e-480a03b1d470",
        "@opprettet": "2022-02-15T13:57:09.458117551"
    }
    """.trimIndent()

    @Language("JSON")
    private fun testInntektsmelding(id: String) = """
    {
        "@event_name": "inntektsmelding",
        "@id": "$id",
        "@opprettet": "2022-02-15T13:57:09.458117551",
        "inntektsmeldingId": "29735b0d-921c-44b3-a4f2-56b3aa1d0e36"
    }
    """.trimIndent()
}