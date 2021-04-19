package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

class DokumentDao(val datasource: DataSource) {
    private val log = LoggerFactory.getLogger(DokumentDao::class.java)

    fun opprett(hendelse: Hendelse) {
        @Language("PostgreSQL")
        val query = "INSERT INTO hendelse(hendelse_id, dokument_id, type) VALUES(?,?,?) ON CONFLICT DO NOTHING"
        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    query,
                    hendelse.hendelseId, hendelse.dokumentId, hendelse.type.name
                ).asUpdate
            )
        }
    }

    fun finnDokumenter(hendelseIder: List<UUID>) = finn(hendelseIder)
        .let { hendelser ->
            (hendelseIder - hendelser.map { hendelse -> hendelse.hendelseId })
                .forEach { log.info("Fant ikke dokumentId for hendelseId $it") }
            Dokumenter(
                sykmelding = hendelser.first { it.type == Dokument.Sykmelding },
                søknad = hendelser.firstOrNull { it.type == Dokument.Søknad },
                inntektsmelding = hendelser.firstOrNull { it.type == Dokument.Inntektsmelding }
            )
        }

    private fun finn(hendelseIder: List<UUID>) = sessionOf(datasource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM hendelse WHERE hendelse_id = ANY((?)::uuid[])"
        session.run(
            queryOf(query, hendelseIder.joinToString(prefix = "{", postfix = "}", separator = ",") { it.toString() })
                .map { row ->
                    Hendelse(
                        dokumentId = UUID.fromString(row.string("dokument_id")),
                        hendelseId = UUID.fromString(row.string("hendelse_id")),
                        type = enumValueOf(row.string("type"))
                    )
                }.asList
        )
    }
}

data class Dokumenter(
    val sykmelding: Hendelse,
    val søknad: Hendelse?,
    val inntektsmelding: Hendelse?
) {
    init {
        require(sykmelding.type == Dokument.Sykmelding)
        søknad?.also { require(it.type == Dokument.Søknad) }
        inntektsmelding?.also { require(it.type == Dokument.Inntektsmelding) }
    }
}

data class Hendelse(
    val dokumentId: UUID,
    val hendelseId: UUID,
    val type: Dokument
)
