package no.nav.helse.spre.saksbehandlingsstatistikk

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*
import javax.sql.DataSource

object TestUtil {
    val dataSource: DataSource = dataSource()
    private fun dataSource(): DataSource {
        val embeddedPostgres = EmbeddedPostgres.builder().setPort(56789).start()
        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }
        return HikariDataSource(hikariConfig)
            .apply {
                Flyway
                    .configure()
                    .dataSource(this)
                    .load().also(Flyway::migrate)
            }
    }

    fun finnSøknadDokumentId(søknadHendelseId: UUID) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM søknad WHERE hendelse_id = ?::uuid"
        session.run(
            queryOf(query, søknadHendelseId)
                .map { row -> UUID.fromString(row.string("dokument_id")) }.asSingle
        )
    }

    fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(objectMapper.readTree(expected), objectMapper.readTree(actual)) {
            "expected: $expected, actual: $actual"
        }
    }



    fun NyttDokumentData.json(eventType: String = "sendt_søknad_nav") =
        """{
            "@event_name": "$eventType",
            "@id": "${this.hendelseId}",
            "id": "${this.søknadId}",
            "@opprettet": "${this.hendelseOpprettet}"
        }"""

    val VedtakFattetData.json
        get() =
            """{
            "@event_name": "vedtak_fattet",
            "aktørId": "$aktørId",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "@opprettet": "$avsluttetISpleis"
        }"""

    val VedtakFattetData.jsonAvsluttetUtenGodkjenning
        get() =
            """{
            "@event_name": "vedtak_fattet",
            "aktørId": "$aktørId",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "@opprettet": "$avsluttetISpleis",
            "@forårsaket_av": {
                "event_name": "sendt_søknad_arbeidsgiver"
            }
        }"""

    fun VedtaksperiodeEndretData.json(tilstand: String = "AVVENTER_GODKJENNING") =
        """{
            "@event_name": "vedtaksperiode_endret",
            "gjeldendeTilstand": "$tilstand",
            "hendelser": [${this.hendelser.joinToString { """"$it"""" }}],
            "aktørId": "aktørens id",
            "vedtaksperiodeId": "${this.vedtaksperiodeId}"
        }"""

    val VedtaksperiodeGodkjentData.json
        get() =
            """{
            "@event_name": "vedtaksperiode_godkjent",
            "@opprettet": "$vedtakFattet",
            "saksbehandlerIdent": "$saksbehandlerIdent",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""
}

class LokalUtgiver : Utgiver {
    val meldinger: MutableList<StatistikkEvent> = mutableListOf()
    override fun publiserStatistikk(statistikkEvent: StatistikkEvent) {
        meldinger.add(statistikkEvent)
    }
}



