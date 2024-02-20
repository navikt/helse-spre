package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.automatiskBehandling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.beslutterIdent
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireAutomatiskBehandling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireSaksbehandlerIdent
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.saksbehandlerIdent
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.UUID

internal class VedtaksperiodeGodkjent(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val saksbehandlerIdent: String?,
    private val beslutterIdent: String?,
    private val automatiskBehandling: Boolean
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val generasjonId = behandlingshendelseDao.forrigeBehandlingId(vedtaksperiodeId.asSakId()) ?: return false
        val saksbehandlerEnhet = null // TODO slå opp mot NOM
        val beslutterEnhet = null // TODO slå opp mot NOM
        val builder = behandlingshendelseDao.initialiser(generasjonId) ?: return false
        val ny = builder
            .behandlingstatus(AVSLUTTET)
            .behandlingsresultat(VEDTATT)
            .behandlingsmetode(if (automatiskBehandling) AUTOMATISK else MANUELL)
            .saksbehandlerEnhet(saksbehandlerEnhet)
            .beslutterEnhet(beslutterEnhet)
            .build(opprettet)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

    internal companion object {
        private const val eventName = "vedtaksperiode_godkjent"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireVedtaksperiodeId()
                packet.requireSaksbehandlerIdent()
                packet.requireAutomatiskBehandling()
            },
            opprett = { packet -> VedtaksperiodeGodkjent(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                saksbehandlerIdent = if (packet.automatiskBehandling) null else packet.saksbehandlerIdent,
                beslutterIdent = packet.beslutterIdent,
                automatiskBehandling = packet.automatiskBehandling
            )}
        )
    }
}