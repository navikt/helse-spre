package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.enhet.NavOrganisasjonsmasterClient
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.GODKJENT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.enhet.AutomatiskEnhet
import no.nav.helse.spre.styringsinfo.teamsak.enhet.Enhet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal class VedtaksperiodeGodkjent(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val behandlingId: UUID,
    private val saksbehandlerEnhet: Enhet,
    private val beslutterEnhet: Enhet,
    private val automatiskBehandling: Boolean,
    private val totrinnsbehandling: Boolean
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(BehandlingId(behandlingId))
        val hendelsesmetode = if (automatiskBehandling) AUTOMATISK else if (totrinnsbehandling) TOTRINNS else MANUELL

        val ny = builder
            .behandlingstatus(GODKJENT)
            .enheter(saksbehandlerEnhet, beslutterEnhet)
            .build(opprettet, hendelsesmetode)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtaksperiode_godkjent"

        internal fun river(
            rapidsConnection: RapidsConnection,
            hendelseDao: HendelseDao,
            behandlingshendelseDao: BehandlingshendelseDao,
            nom: NavOrganisasjonsmasterClient
        ) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.interestedInBeslutterIdent()
                packet.requireVedtaksperiodeId()
                packet.requireSaksbehandlerIdent()
                packet.requireAutomatiskBehandling()
            },
            opprett = { packet -> VedtaksperiodeGodkjent(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId,
                saksbehandlerEnhet = packet.enhet(nom, packet.saksbehandlerIdent),
                beslutterEnhet = packet.enhet(nom, packet.beslutterIdent),
                automatiskBehandling = packet.automatiskBehandling,
                totrinnsbehandling = packet.saksbehandlerIdent != null && packet.beslutterIdent != null
            )}
        )

        private fun JsonMessage.enhet(nom: NavOrganisasjonsmasterClient, ident: String?): Enhet {
            if (automatiskBehandling || ident == null) return AutomatiskEnhet
            return nom.hentEnhet(ident, LocalDate.now(), hendelseId.toString())
        }

        private fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandler.ident") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        private val JsonMessage.saksbehandlerIdent get() = this["saksbehandler.ident"].asText().takeUnless { it.isBlank() }
        private fun JsonMessage.interestedInBeslutterIdent() = interestedIn("beslutter.ident")
        private val JsonMessage.beslutterIdent get() = this["beslutter.ident"].asText().takeUnless { it.isBlank() }
        private fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
        private val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
    }
}