package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*

class DokumentAliasRiverTest {

    private lateinit var testDataSource: TestDataSource
    private lateinit var mappingDao: MappingDao
    private lateinit var testRapid: TestRapid

    @BeforeEach
    fun setup() {
        testDataSource = databaseContainer.nyTilkobling()
        mappingDao = MappingDao(testDataSource.ds)
        testRapid = TestRapid().apply {
            DokumentAliasRiver(this, mappingDao)
        }
    }

    @AfterEach
    fun before() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    @Test
    fun `lager mapping på inntektsmelding`() {
        val eksternDokumentId = "29735b0d-921c-44b3-a4f2-56b3aa1d0e36".toUUID()
        val internDokumentId = "ebe84db6-049d-49c4-b09e-480a03b1d470".toUUID()
        testRapid.sendTestMessage(testInntektsmelding(internDokumentId, eksternDokumentId))
        assertEquals(eksternDokumentId, mappingDao.hentInntektsmeldingId(internDokumentId))
    }

    @Test
    fun `lager mapping på søknad`() {
        val eksternDokumentId = "8f1c5afa-3bec-4856-80b8-62b2f10c055a".toUUID()
        val internDokumentId = "e3095fa0-c5c2-414b-9492-5146671310d5".toUUID()
        testRapid.sendTestMessage(testSøknad(internDokumentId, eksternDokumentId))
        assertEquals(eksternDokumentId, mappingDao.hentSøknadId(internDokumentId))
    }

    @Test
    fun `lager mapping på sykmelding`() {
        val eksternDokumentId = "2fa20e0e-f816-45ea-a746-70365af98645".toUUID()
        val internDokumentId = "b0b9ab8c-3525-46b0-949f-63bbc890b17e".toUUID()
        testRapid.sendTestMessage(testSykmelding(internDokumentId, eksternDokumentId))
        assertEquals(eksternDokumentId, mappingDao.hentSykmeldingId(internDokumentId))
    }

    private fun testInntektsmelding(internDokumentId: UUID, eksternDokumentId: UUID) = dokumentalias(DokumentIdType.Inntektsmelding, "inntektsmelding", internDokumentId, eksternDokumentId)
    private fun testSøknad(internDokumentId: UUID, eksternDokumentId: UUID) = dokumentalias(DokumentIdType.Søknad, "sendt_søknad_nav", internDokumentId, eksternDokumentId)
    private fun testSykmelding(internDokumentId: UUID, eksternDokumentId: UUID) = dokumentalias(DokumentIdType.Sykmelding, "ny_søknad", internDokumentId, eksternDokumentId)

    @Language("JSON")
    private fun dokumentalias(dokumenttype: DokumentIdType, hendelsenavn: String, internDokumentId: UUID, eksternDokumentId: UUID) = """
    {
        "@event_name": "dokument_alias",
        "@id": "${UUID.randomUUID()}",
        "dokumenttype": "${dokumenttype.name.uppercase()}",
        "hendelsenavn": "$hendelsenavn",
        "intern_dokument_id": "$internDokumentId",
        "@opprettet": "2022-02-15T13:57:09.458117551",
        "ekstern_dokument_id": "$eksternDokumentId"
    }"""
}