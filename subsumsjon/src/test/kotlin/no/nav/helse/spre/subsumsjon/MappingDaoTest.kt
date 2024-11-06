package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class MappingDaoTest {

    private lateinit var testDataSource: TestDataSource
    private lateinit var mappingDao: MappingDao

    @BeforeEach
    fun before() {
        testDataSource = databaseContainer.nyTilkobling()
        mappingDao = MappingDao(testDataSource.ds)
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(testDataSource)
    }

    @Test
    fun `lagre og hent`() {
        mappingDao.lagre(
            UUID.fromString("e07f59f8-3cf0-454d-bf3b-02058ef7ceeb"),
            UUID.fromString("820a302d-27fd-4c8c-b5d1-49f9126fc89d"),
            DokumentIdType.Søknad,
            "test_event",
            LocalDateTime.now()
        )
        val result = mappingDao.hentSøknadId(UUID.fromString("e07f59f8-3cf0-454d-bf3b-02058ef7ceeb"))
        assertEquals(UUID.fromString("820a302d-27fd-4c8c-b5d1-49f9126fc89d"), result)
    }

    @Test
    fun `hent en hendelse som ikke er lagret`() {
        val result = mappingDao.hentSøknadId(UUID.fromString("e07f59f8-3cf0-454d-bf3b-02058ef7ceeb"))
        assertNull(result)
    }
}