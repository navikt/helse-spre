package no.nav.helse.spre.saksbehandlingsstatistikk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.testcontainers.containers.PostgreSQLContainer

object TestUtil {
    val dataSource: DataSource = dataSource()
    private fun dataSource(): DataSource {
        val postgres = PostgreSQLContainer<Nothing>("postgres:13").also { it.start() }
        val dataSource: DataSource =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 3
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
            })

        dataSource.apply {
            Flyway
                .configure()
                .dataSource(dataSource)
                .load().also(Flyway::migrate)
        }

        return dataSource
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


    fun SøknadData.json(eventType: String = "sendt_søknad_nav") =
        """{
            "@event_name": "$eventType",
            "@id": "${this.hendelseId}",
            "id": "${this.søknadId}",
            "@opprettet": "${this.hendelseOpprettet}",
            "korrigerer": ${this.korrigerer?.let { """"$it"""" }}
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
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "automatiskBehandling": "$automatiskBehandling"
        }"""

    val VedtaksperiodeForkastetData.json
        get() =
            """{
            "@event_name": "vedtaksperiode_forkastet",
            "@opprettet": "$vedtaksperiodeForkastet",
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "aktørId": "$aktørId"
        }"""

    val VedtaksperiodeAvvistData.json
        get() =
            """{
            "@event_name": "vedtaksperiode_godkjent",
            "@opprettet": "$vedtakFattet",
            "saksbehandlerIdent": "$saksbehandlerIdent",
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "automatiskBehandling": "$automatiskBehandling"
        }"""

    val GodkjenningsBehovLøsningData.json
        get() =
            """{
            "@behov": ["Godkjenning"],
            "vedtaksperiodeId": "$vedtaksperiodeId",
            "@løsning": {
                "Godkjenning": {
                    "godkjenttidspunkt": "$vedtakFattet",
                    "saksbehandlerIdent": "$saksbehandlerIdent",
                    "godkjent": false,
                    "automatiskBehandling": "$automatiskBehandling"
                }
            }
        }"""
}

class LokalUtgiver : Utgiver {
    val meldinger: MutableList<StatistikkEvent> = mutableListOf()
    override fun publiserStatistikk(statistikkEvent: StatistikkEvent) {
        meldinger.add(statistikkEvent)
    }
}



