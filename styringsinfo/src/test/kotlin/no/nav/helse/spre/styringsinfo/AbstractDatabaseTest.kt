package no.nav.helse.spre.styringsinfo

import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.github.navikt.tbd_libs.test_support.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

val databaseContainer = DatabaseContainers.container("spre-styringsinfo", CleanupStrategy.tables("behandlingshendelse,hendelse"), walLevelLogical = true)

abstract class AbstractDatabaseTest {
    protected lateinit var testDataSource: TestDataSource

    @BeforeEach
    fun before() {
        testDataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(testDataSource)
    }
}

