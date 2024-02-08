package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.*

internal class GenerasjonForkastet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val vedtaksperiodeId: UUID
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao): Boolean {
        val builders = behandlingDao.initialiser(SakId(vedtaksperiodeId)).takeUnless { it.isEmpty() } ?: return false
        builders.forEach { builder ->
            val ny = builder
                .behandlingstatus(Behandling.Behandlingstatus.Avsluttet)
                .behandlingsresultat(Behandling.Behandlingsresultat.Avbrutt)
                .build(opprettet)
            behandlingDao.lagre(ny)
        }
        return true
    }

    internal companion object {
        private const val eventName = "generasjon_forkastet"

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            valider = { packet -> packet.requireVedtaksperiodeId() },
            opprett = { packet -> GenerasjonForkastet(
                id = packet.hendelseId,
                blob = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId
            )}
        )
    }
}