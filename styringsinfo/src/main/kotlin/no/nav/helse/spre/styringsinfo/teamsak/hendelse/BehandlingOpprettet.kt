package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SAKSBEHANDLER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsmetode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.UUID

internal class BehandlingOpprettet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val behandlingId: UUID,
    private val aktørId: String,
    private val behandlingskilde: Behandlingskilde,
    private val behandlingstype: Behandlingstype
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val sakId = SakId(vedtaksperiodeId)
        val behandlingskilde = behandlingskilde.avsender.behandlingskilde

        val behandling = Behandling(
            sakId = sakId,
            behandlingId = BehandlingId(behandlingId),
            relatertBehandlingId = behandlingshendelseDao.behandlingIdFraForrigeBehandlingshendelse(sakId),
            aktørId = aktørId,
            mottattTid = this.behandlingskilde.innsendt,
            registrertTid = this.behandlingskilde.registrert,
            funksjonellTid = opprettet,
            behandlingstatus = REGISTRERT,
            behandlingstype = behandlingstype.behandlingstype,
            behandlingskilde = behandlingskilde,
            behandlingsmetode = if (behandlingskilde == SAKSBEHANDLER) MANUELL else AUTOMATISK
        )
        behandlingshendelseDao.lagre(behandling, this.id)
        return true
    }

    internal class Behandlingskilde(internal val innsendt: LocalDateTime, internal val registrert: LocalDateTime, internal val avsender: Avsender)
    internal class Avsender(val verdi: String)
    internal class Behandlingstype(val verdi: String)

    internal companion object {
        private val Avsender.behandlingskilde get() = when (verdi) {
            "SYKMELDT" -> Behandling.Behandlingskilde.SYKMELDT
            "ARBEIDSGIVER" -> Behandling.Behandlingskilde.ARBEIDSGIVER
            "SAKSBEHANDLER" -> SAKSBEHANDLER
            "SYSTEM" -> Behandling.Behandlingskilde.SYSTEM
            else -> throw IllegalStateException("Kjenner ikke til kildeavsender $verdi")
        }

        private val Behandlingstype.behandlingstype get() = when (verdi) {
            "TilInfotrygd", "Søknad" -> Behandling.Behandlingstype.SØKNAD
            "Omgjøring" -> Behandling.Behandlingstype.OMGJØRING
            "Revurdering" -> Behandling.Behandlingstype.REVURDERING
            else -> throw IllegalStateException("Kjenner ikke til behandlingstype $verdi")
        }

        private const val eventName = "behandling_opprettet"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireVedtaksperiodeId()
                packet.requireBehandlingId()
                packet.requireKey("aktørId", "kilde.registrert", "kilde.innsendt", "kilde.avsender", "type")
            },
            opprett = { packet -> BehandlingOpprettet(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                aktørId = packet["aktørId"].asText(),
                behandlingskilde = Behandlingskilde(
                    innsendt = LocalDateTime.parse(packet["kilde.innsendt"].asText()),
                    registrert = LocalDateTime.parse(packet["kilde.registrert"].asText()),
                    avsender = Avsender(packet["kilde.avsender"].asText())
                ),
                behandlingstype = Behandlingstype(packet["type"].asText())
            )}
        )
    }
}