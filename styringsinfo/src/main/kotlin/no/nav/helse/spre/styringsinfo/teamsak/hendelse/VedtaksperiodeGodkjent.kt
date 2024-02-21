package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.nom.Nom
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.asSakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.automatiskBehandling
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.beslutterIdent
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.saksbehandlerIdent
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperiodeGodkjent(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val saksbehandlerEnhet: String?,
    private val beslutterEnhet: String?,
    private val automatiskBehandling: Boolean
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val generasjonId = behandlingshendelseDao.forrigeBehandlingId(vedtaksperiodeId.asSakId()) ?: return false
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

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, nom: Nom) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.interestedIn("beslutterIdent")
                packet.requireVedtaksperiodeId()
                packet.requireSaksbehandlerIdent()
                packet.requireAutomatiskBehandling()
            },
            opprett = { packet -> VedtaksperiodeGodkjent(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                saksbehandlerEnhet = if (packet.automatiskBehandling) null else nom.hentEnhet(packet.saksbehandlerIdent, LocalDate.now(), packet.hendelseId.toString()),
                beslutterEnhet = nom.hentEnhet(packet.beslutterIdent, LocalDate.now(), packet.hendelseId.toString()),
                automatiskBehandling = packet.automatiskBehandling
            )}
        )

        internal fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandlerIdent") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        internal fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
    }
}