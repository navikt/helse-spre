package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.nom.Enhet
import no.nav.helse.nom.Nom
import no.nav.helse.nom.Saksbehandler
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.GODKJENT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.asSakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeBeslutning.Companion.Beslutning.Avvist
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeBeslutning.Companion.Beslutning.Godkjent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperiodeBeslutning private constructor(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val saksbehandlerEnhet: String?,
    private val beslutterEnhet: String?,
    private val automatiskBehandling: Boolean,
    beslutning: Beslutning
) : Hendelse {
    private val behandlingstatus = when (beslutning) { Godkjent -> GODKJENT; Avvist -> AVSLUTTET }
    private val behandlingsresultat = when (beslutning) { Godkjent -> VEDTATT; Avvist -> AVBRUTT }
    override val type = beslutning.eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val behandlingId = behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(vedtaksperiodeId.asSakId()) ?: return false
        val builder = behandlingshendelseDao.initialiser(behandlingId) ?: return false
        val behandlingsmetode = if (automatiskBehandling) AUTOMATISK else MANUELL
        val ny = builder
            .behandlingstatus(behandlingstatus)
            .behandlingsresultat(behandlingsresultat)
            .saksbehandlerEnhet(saksbehandlerEnhet)
            .beslutterEnhet(beslutterEnhet)
            .build(opprettet, behandlingsmetode)
        behandlingshendelseDao.lagre(ny, this.id)
        return true
    }

    internal companion object {
        private enum class Beslutning(val eventName: String) { Godkjent("vedtaksperiode_godkjent"), Avvist("vedtaksperiode_avvist") }

        internal fun vedtaksperiodeAvvist(id: UUID, opprettet: LocalDateTime, data: JsonNode, vedtaksperiodeId: UUID, saksbehandlerEnhet: String?, beslutterEnhet: String?, automatiskBehandling: Boolean) =
            VedtaksperiodeBeslutning(id, opprettet, data, vedtaksperiodeId, saksbehandlerEnhet, beslutterEnhet, automatiskBehandling, Avvist)
        internal fun vedtaksperiodeGodkjent(id: UUID, opprettet: LocalDateTime, data: JsonNode, vedtaksperiodeId: UUID, saksbehandlerEnhet: String?, beslutterEnhet: String?, automatiskBehandling: Boolean) =
            VedtaksperiodeBeslutning(id, opprettet, data, vedtaksperiodeId, saksbehandlerEnhet, beslutterEnhet, automatiskBehandling, Godkjent)

        internal fun vedtaksperiodeAvvistRiver(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, nom: Nom) =
            river(rapidsConnection, hendelseDao, behandlingshendelseDao, nom, Avvist)
        internal fun vedtaksperiodeGodkjentRiver(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, nom: Nom) =
            river(rapidsConnection, hendelseDao, behandlingshendelseDao, nom, Godkjent)

        private fun river(
            rapidsConnection: RapidsConnection,
            hendelseDao: HendelseDao,
            behandlingshendelseDao: BehandlingshendelseDao,
            nom: Nom,
            beslutning: Beslutning,
        ) = HendelseRiver(
            eventName = beslutning.eventName,
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
                beslutning = beslutning
            )}
        )

        private fun JsonMessage.enhet(nom: Nom, ident: Saksbehandler?): Enhet? {
            if (automatiskBehandling || ident == null) return null
            return nom.hentEnhet(ident, LocalDate.now(), hendelseId.toString())
        }

        private val JsonMessage.saksbehandlerIdent get() = this["saksbehandlerIdent"].asText().takeUnless { it.isBlank() }
        private val JsonMessage.beslutterIdent get() = this["beslutterIdent"].asText().takeUnless { it.isBlank() }
        private fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandlerIdent") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        private fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
        private val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
    }
}