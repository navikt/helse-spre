package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.generasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import java.time.LocalDateTime
import java.util.*

internal class AvsluttetUtenVedtak(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao) {
        val builder = behandlingDao.initialiser(BehandlingId(generasjonId)) ?: return // Avsluttet uten vedtak for noe vi ikke har fått generasjon opprettet for
        val ny = builder
            .behandlingstatus(Behandling.Behandlingstatus.Avsluttet)
            .behandlingsresultat(Behandling.Behandlingsresultat.Henlagt)
            .funksjonellTid(opprettet)
            .build()
        behandlingDao.lagre(ny)
    }

    internal companion object {
        private val eventName = "avsluttet_uten_vedtak"

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            opprett = { packet -> AvsluttetUtenVedtak(
                id = packet.hendelseId,
                blob = packet.blob,
                opprettet = packet.opprettet,
                generasjonId = packet.generasjonId
            )}
        )
    }
}