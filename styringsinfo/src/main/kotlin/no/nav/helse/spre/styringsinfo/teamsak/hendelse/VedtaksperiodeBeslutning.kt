package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.nom.Enhet
import no.nav.helse.nom.Nom
import no.nav.helse.nom.Saksbehandler
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
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

internal class VedtaksperiodeBeslutning(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val saksbehandlerEnhet: String?,
    private val beslutterEnhet: String?,
    private val automatiskBehandling: Boolean,
    private val behandlingsresultat: Behandling.Behandlingsresultat,
    private val eventName: String,
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val generasjonId = behandlingshendelseDao.forrigeBehandlingId(vedtaksperiodeId.asSakId()) ?: return false
        val builder = behandlingshendelseDao.initialiser(generasjonId) ?: return false
        val ny = builder
            .behandlingstatus(AVSLUTTET)
            .behandlingsresultat(behandlingsresultat)
            .behandlingsmetode(if (automatiskBehandling) AUTOMATISK else MANUELL)
            .saksbehandlerEnhet(saksbehandlerEnhet)
            .beslutterEnhet(beslutterEnhet)
            .build(opprettet)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

    internal companion object {

        internal fun vedtaksperiodeAvvistRiver(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, nom: Nom) =
            river(rapidsConnection, hendelseDao, behandlingshendelseDao, nom, AVBRUTT, "vedtaksperiode_avvist")
        internal fun vedtaksperiodeGodkjentRiver(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, nom: Nom) =
            river(rapidsConnection, hendelseDao, behandlingshendelseDao, nom, VEDTATT, "vedtaksperiode_godkjent")

        private fun river(rapidsConnection: RapidsConnection,
                          hendelseDao: HendelseDao,
                          behandlingshendelseDao: BehandlingshendelseDao,
                          nom: Nom,
                          behandlingsresultat: Behandling.Behandlingsresultat,
                          eventName: String) = HendelseRiver(
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
            opprett = { packet -> VedtaksperiodeBeslutning(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                saksbehandlerEnhet = packet.enhet(nom, packet.saksbehandlerIdent),
                beslutterEnhet = packet.enhet(nom, packet.beslutterIdent),
                automatiskBehandling = packet.automatiskBehandling,
                behandlingsresultat = behandlingsresultat,
                eventName = eventName
            )}
        )

        private fun JsonMessage.enhet(nom: Nom, ident: Saksbehandler?): Enhet? {
            if (automatiskBehandling || ident == null) return null
            return nom.hentEnhet(ident, LocalDate.now(), hendelseId.toString())
        }

        private fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandlerIdent") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        private fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
    }
}