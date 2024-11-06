package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import java.util.*

class SøknadRiverTest {

    private lateinit var testDataSource: TestDataSource
    private lateinit var mappingDao: MappingDao
    private lateinit var river: SøknadRiver
    private val testRapid = TestRapid()

    @BeforeEach
    fun before() {
        testDataSource = databaseContainer.nyTilkobling()
        mappingDao = MappingDao(testDataSource.ds)
        river = SøknadRiver(testRapid, mappingDao)
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(testDataSource)
        testRapid.reset()
    }

    @Test
    fun `lager mapping på sendt_søknad_nav`() {
        testRapid.sendTestMessage(testSendtSøknadNav("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        Assertions.assertEquals(
            UUID.fromString("3721cc0d-33f6-4df5-8d2d-41e72dcf648b"),
            mappingDao.hentSøknadId(UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        )
    }

    @Test
    fun `lager mapping på sendt_søknad_arbeidsgiver`() {
        testRapid.sendTestMessage(testSendtSøknadArbeidsgiver("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        Assertions.assertEquals(
            UUID.fromString("3721cc0d-33f6-4df5-8d2d-41e72dcf648b"),
            mappingDao.hentSøknadId(UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        )
    }

    @Test
    fun `kræsj på feilaktige søknader`() {
        assertThrows<IllegalArgumentException> {  testRapid.sendTestMessage(badTestSøknad) }
    }

    @Test
    fun `kan hente sykmeldingId fra søknad`() {
        testRapid.sendTestMessage(testSendtSøknadArbeidsgiver("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        Assertions.assertEquals(
            UUID.fromString("4221cc0d-33f6-4df5-8d2d-41e72dcf6442"),
            mappingDao.hentSykmeldingId(UUID.fromString("6f0a0911-fc3f-4a55-8fb7-8222388b1707"))
        )
    }
}

// Mangler required fields
@Language("JSON")
private val badTestSøknad = """
    {
      "@event_name": "sendt_søknad_nav",
      "@id": "c6dda12e-f46c-4d22-bba6-4363590d66d8",
      "@opprettet": "2022-02-15T13:57:09.158078880"
    }
""".trimIndent()

@Language("JSON")
private fun testSendtSøknadNav(id: String) = """
    {
      "@event_name": "sendt_søknad_nav",
      "@id": "$id",
      "@opprettet": "2022-02-15T13:57:09.158078880",
      "id": "3721cc0d-33f6-4df5-8d2d-41e72dcf648b",
      "sykmeldingId": "4221cc0d-33f6-4df5-8d2d-41e72dcf6442"
    }
""".trimIndent()


@Language("JSON")
private fun testSendtSøknadArbeidsgiver(id: String) = """
    {
      "@event_name": "sendt_søknad_arbeidsgiver",
      "@id": "$id",
      "@opprettet": "2022-02-15T13:57:09.158078880",
      "id": "3721cc0d-33f6-4df5-8d2d-41e72dcf648b",
      "sykmeldingId": "4221cc0d-33f6-4df5-8d2d-41e72dcf6442"
    }
""".trimIndent()