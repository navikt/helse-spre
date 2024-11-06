package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import java.util.*

class InntektsmeldingRiverTest {

    private lateinit var testDataSource: TestDataSource
    private lateinit var mappingDao: MappingDao
    private lateinit var river: InntektsmeldingRiver
    private val testRapid = TestRapid()

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
        mappingDao = MappingDao(testDataSource.ds)
        river = InntektsmeldingRiver(testRapid, mappingDao)
    }

    @AfterEach
    fun before() {
        databaseContainer.droppTilkobling(testDataSource)
        testRapid.reset()
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