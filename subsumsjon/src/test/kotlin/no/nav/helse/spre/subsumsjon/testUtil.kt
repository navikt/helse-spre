package no.nav.helse.spre.subsumsjon

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.testcontainers.containers.PostgreSQLContainer

internal fun resetMappingDb(postgres: PostgreSQLContainer<Nothing>) {
    val dataSourceBuilder = DataSourceBuilder(postgres.jdbcUrl, postgres.username, postgres.password)
    sessionOf(dataSourceBuilder.datasource()).use { session ->
        @Language("PostgreSQL")
        val query = "TRUNCATE TABLE hendelse_dokument_mapping"
        session.run(queryOf(query).asExecute)
    }
}