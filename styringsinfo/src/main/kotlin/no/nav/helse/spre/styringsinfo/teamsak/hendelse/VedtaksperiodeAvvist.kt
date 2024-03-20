package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.NavOrganisasjonsmasterClient
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.Enhet
import no.nav.helse.spre.styringsinfo.teamsak.Saksbehandler
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.asSakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class VedtaksperiodeAvvist(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val saksbehandlerEnhet: String?,
    private val automatiskBehandling: Boolean
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val behandlingId = behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(vedtaksperiodeId.asSakId()) ?: return false
        val builder = behandlingshendelseDao.initialiser(behandlingId) ?: return false
        val hendelsesmetode = if (automatiskBehandling) AUTOMATISK else MANUELL
        val ny = builder
            .avslutt(AVBRUTT)
            .saksbehandlerEnhet(saksbehandlerEnhet)
            .build(opprettet, hendelsesmetode)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtaksperiode_avvist"

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
                packet.requireVedtaksperiodeId()
                packet.requireSaksbehandlerIdent()
                packet.requireAutomatiskBehandling()
            },
            opprett = { packet -> VedtaksperiodeAvvist(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                saksbehandlerEnhet = packet.enhet(nom, packet.saksbehandlerIdent),
                automatiskBehandling = packet.automatiskBehandling
            )}
        )

        private fun JsonMessage.enhet(nom: NavOrganisasjonsmasterClient, ident: Saksbehandler?): Enhet? {
            if (automatiskBehandling || ident == null) return null
            return nom.hentEnhet(ident, LocalDate.now(), hendelseId.toString())
        }

        private val JsonMessage.saksbehandlerIdent get() = this["saksbehandler.ident"].asText().takeUnless { it.isBlank() }
        private fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandler.ident") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        private fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
        private val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
    }
}