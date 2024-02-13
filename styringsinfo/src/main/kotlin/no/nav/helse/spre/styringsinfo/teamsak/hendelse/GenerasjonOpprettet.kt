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
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.generasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireGenerasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.LocalDateTime
import java.util.UUID

internal class GenerasjonOpprettet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val data: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val generasjonId: UUID,
    private val aktørId: String,
    private val generasjonkilde: Generasjonkilde,
    private val generasjonstype: Generasjonstype
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val sakId = SakId(vedtaksperiodeId)
        val behandlingskilde = generasjonkilde.avsender.behandlingskilde

        val behandling = Behandling(
            sakId = sakId,
            behandlingId = BehandlingId(generasjonId),
            relatertBehandlingId = behandlingshendelseDao.forrigeBehandlingId(sakId),
            aktørId = aktørId,
            mottattTid = generasjonkilde.innsendt,
            registrertTid = generasjonkilde.registrert,
            funksjonellTid = generasjonkilde.registrert,
            behandlingstatus = REGISTRERT,
            behandlingstype = generasjonstype.behandlingstype,
            behandlingskilde = behandlingskilde,
            behandlingsmetode = if (behandlingskilde == SAKSBEHANDLER) MANUELL else AUTOMATISK
        )
        behandlingshendelseDao.lagre(behandling, this.id)
        return true
    }

    internal class Generasjonkilde(internal val innsendt: LocalDateTime, internal val registrert: LocalDateTime, internal val avsender: Avsender)
    internal class Avsender(val verdi: String)
    internal class Generasjonstype(val verdi: String)

    internal companion object {
        private val Avsender.behandlingskilde get() = when (verdi) {
            "SYKMELDT" -> Behandling.Behandlingskilde.SYKMELDT
            "ARBEIDSGIVER" -> Behandling.Behandlingskilde.ARBEIDSGIVER
            "SAKSBEHANDLER" -> SAKSBEHANDLER
            "SYSTEM" -> Behandling.Behandlingskilde.SYSTEM
            else -> throw IllegalStateException("Kjenner ikke til kildeavsender $verdi")
        }

        private val Generasjonstype.behandlingstype get() = when (verdi) {
            "Førstegangsbehandling", "TilInfotrygd" -> Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING
            "Omgjøring" -> Behandling.Behandlingstype.OMGJØRING
            "Revurdering" -> Behandling.Behandlingstype.REVURDERING
            else -> throw IllegalStateException("Kjenner ikke til generasjontype $verdi")
        }

        private const val eventName = "generasjon_opprettet"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireVedtaksperiodeId()
                packet.requireGenerasjonId()
                packet.requireKey("aktørId", "kilde.registrert", "kilde.innsendt", "kilde.avsender", "type")
            },
            opprett = { packet -> GenerasjonOpprettet(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                generasjonId = packet.generasjonId,
                vedtaksperiodeId = packet.vedtaksperiodeId,
                aktørId = packet["aktørId"].asText(),
                generasjonkilde = Generasjonkilde(
                    innsendt = LocalDateTime.parse(packet["kilde.innsendt"].asText()),
                    registrert = LocalDateTime.parse(packet["kilde.registrert"].asText()),
                    avsender = Avsender(packet["kilde.avsender"].asText())
                ),
                generasjonstype = Generasjonstype(packet["type"].asText())
            )}
        )
    }
}