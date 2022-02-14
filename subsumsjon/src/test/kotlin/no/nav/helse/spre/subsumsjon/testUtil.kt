package no.nav.helse.spre.subsumsjon

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon.DataSourceBuilder
import org.intellij.lang.annotations.Language
import org.testcontainers.containers.PostgreSQLContainer

internal fun resetMappingDb(postgres: PostgreSQLContainer<Nothing>) {
    sessionOf(DataSourceBuilder(postgres.jdbcUrl, postgres.username, postgres.password).getMigratedDataSource()).use { session ->
        @Language("PostgreSQL")
        val query = "TRUNCATE TABLE hendelse_id_mapping"
        session.run(queryOf(query).asExecute)
    }
}