package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.Automatisk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.Vedtatt
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsstatus.Avsluttet
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.generasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireGenerasjonId
import java.time.LocalDateTime
import java.util.*

internal class AvsluttetMedVedtak(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao): Boolean {
        val builder = behandlingDao.initialiser(BehandlingId(generasjonId)) ?: return false
        val ny = builder
            .behandlingsresultat(Vedtatt)
            .build(
                funksjonellTid = opprettet,
                behandlingsstatus = Avsluttet,
                behandlingsmetode = Automatisk // TODO: Tja, dette vet vi jo egentlig ikke.. Vi må kanskje bytte ut AvsluttetMedVedtak -> VedtaksperiodeGodkjent
            )
        behandlingDao.lagre(ny)
        return true
    }

    internal companion object {
        private const val eventName = "avsluttet_med_vedtak"

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            valider = { packet -> packet.requireGenerasjonId() },
            opprett = { packet -> AvsluttetMedVedtak(
                id = packet.hendelseId,
                blob = packet.blob,
                opprettet = packet.opprettet,
                generasjonId = packet.generasjonId
            )}
        )
    }
}