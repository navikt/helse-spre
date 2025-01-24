package no.nav.helse.spre.styringsinfo

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.Clock.SYSTEM
import io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry.defaultRegistry
import java.io.File
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.tidspunkt
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao

fun main() {
    // Send inn jdbc url som printes i consolen når du kobler til nais postgres proxy
    val manuell = ManuellInngripen(
        jdbcUrl = "<fyll-meg>"
    )
    // For behandling opprettet må du først finne aktørId fra fødselsnummer i meldingen (f.eks i Spanner) ettersom den i appen slåes opp mot Speed (PDL)
    manuell.leggTilBehandlingOpprettet(
        path = "<path-til-json-meldingen-appen-ikke-har-håndtert-f-eks-absolute-path-til-scratchfil>",
        aktørId = "<aktørId-verdi-du-finner-et-sted>"
    )
}

private class ManuellInngripen(jdbcUrl: String) {
    private val dataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
    })
    private val hendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingstatusDao = PostgresBehandlingshendelseDao(dataSource)

    fun leggTilBehandlingOpprettet(path: String, aktørId: String) {
        val packet = path.jsonMessage
        val id = packet.hendelseId
        if (behandlingstatusDao.harHåndtertHendelseTidligere(id)) return println("Hendelse med @id $id er allerede håndtert.")

        BehandlingOpprettet.valider(packet)
        val behandlingOpprettet = BehandlingOpprettet.opprett(packet, aktørId)
        hendelseDao.lagre(behandlingOpprettet)
        behandlingOpprettet.håndter(behandlingstatusDao)
        println("La til BehandlingOpprettet med @id $id")
    }

    private val meterRegistry = PrometheusMeterRegistry(DEFAULT, defaultRegistry, SYSTEM)
    private val String.jsonMessage get() = File(this).readText().let {
        JsonMessage(it, MessageProblems(it), meterRegistry).also { packet -> defaultValidering(packet) }
    }
    private fun defaultValidering(packet: JsonMessage) = with(packet) {
        requireKey("@event_name")
        require("@opprettet") { opprettet -> opprettet.tidspunkt }
        require("@id") { id -> UUID.fromString(id.asText()) }
        interestedIn("vedtaksperiodeId", "behandlingId")
    }
}

