package no.nav.helse.spre.styringsinfo

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.tidspunkt
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet

fun main() {
    // Send inn jdbc url som printes i consolen når du kobler til nais postgres proxy
    val manuell = ManuellInngripen(
        jdbcUrl = "<fyll-meg>"
    )
    // For behandling_opprettet må du først finne aktørId fra fødselsnummer i meldingen (f.eks i Spanner) ettersom den i appen slåes opp mot Speed (PDL)
    // KOMMENTER MEG UT OM DU IKKE SKAL HÅNDETERE EN BEHANDLING_OPPRETTET
    manuell.håndterBehandlingOpprettet(
        path = "<path-til-json-meldingen-appen-ikke-har-håndtert-f-eks-absolute-path-til-scratchfil>",
        aktørId = "<aktørId-verdi-du-finner-et-sted>"
    )
    // Noen ganger kommer det av ukjente årsaker vedtak_fattet _uten_ tags.
    // Dette løses ved å ta meldingen som mangler tags i en scratchfil og legge til de riktige tagsene (ved f.eks. å spore opp utkast_til_vedtak og finne tags der)
    // Når det er 🦌 kan du kjøre denne funksjonen.
    // KOMMENTER MEG UT OM DU IKKE SKAL HÅNDETER EN VEDTAK_FATTET
    manuell.håndterVedtakFattet(
        path = "<path-til-json-meldingen-appen-ikke-har-håndtert-f-eks-absolute-path-til-scratchfil>"
    )
}

private class ManuellInngripen(jdbcUrl: String) {
    private val dataSource = HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
    })
    private val hendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingstatusDao = PostgresBehandlingshendelseDao(dataSource)

    fun håndterBehandlingOpprettet(path: String, aktørId: String) {
        val packet = packetOrNull(path, "behandling_opprettet") ?: return
        BehandlingOpprettet.valider(packet)
        håndter(BehandlingOpprettet.opprett(packet, aktørId))
    }

    fun håndterVedtakFattet(path: String) {
        val packet = packetOrNull(path, "vedtak_fattet") ?: return
        // Det som håndteres som "preconditions" i appen til vanlig.
        packet.interestedIn("sykepengegrunnlagsfakta", "utbetalingId")
        check(!packet["sykepengegrunnlagsfakta"].isMissingOrNull()) { "vedtak_fattet uten sykepengegrunnlagsfakta skal ikke håndteres" }
        check(!packet["utbetalingId"].isMissingOrNull()) { "vedtak_fattet uten utbetalingId skal ikke håndteres" }
        VedtakFattet.valider(packet)
        håndter(VedtakFattet.opprett(packet))
    }

    private fun håndter(hendelse: Hendelse) {
        hendelseDao.lagre(hendelse)
        hendelse.håndter(behandlingstatusDao)
        println("Håndtert hendelse med med @id ${hendelse.id}")
    }

    private fun packetOrNull(path: String, eventName: String): JsonMessage? {
        val packet = path.jsonMessage(eventName)
        val id = packet.hendelseId
        if (behandlingstatusDao.harHåndtertHendelseTidligere(id)) {
            println("Hendelse med @id $id er allerede håndtert.")
            return null
        }
        return packet
    }

    private fun String.jsonMessage(eventName: String) = File(this).readText().let {
        JsonMessage(it, MessageProblems(it)).also { packet -> defaultValidering(packet, eventName) }
    }
    private fun defaultValidering(packet: JsonMessage, eventName: String) = with(packet) {
        requireValue("@event_name", eventName)
        require("@opprettet") { opprettet -> opprettet.tidspunkt }
        require("@id") { id -> UUID.fromString(id.asText()) }
        interestedIn("vedtaksperiodeId", "behandlingId")
    }
}

