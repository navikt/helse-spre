package no.nav.helse.spre.subsumsjon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MappingDaoTest {

    private lateinit var postgres: PostgreSQLContainer<Nothing>
    private lateinit var mappingDao: MappingDao

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

    }

    @BeforeEach
    fun before() {
        resetMappingDb(postgres)
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