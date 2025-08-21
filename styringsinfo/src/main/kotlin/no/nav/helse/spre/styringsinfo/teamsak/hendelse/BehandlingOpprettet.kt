package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.SpeedClient
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SAKSBEHANDLER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
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
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.tidspunkt
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.vedtaksperiodeId
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.interestedInYrkesaktivitetstype
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.yrkesaktivitetstype

internal class BehandlingOpprettet(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val yrkesaktivitetstype: String,
    private val vedtaksperiodeId: UUID,
    private val behandlingId: UUID,
    private val aktørId: String,
    private val behandlingskilde: Behandlingskilde,
    private val behandlingstype: Behandlingstype,
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        if(behandlingshendelseDao.harLagretBehandingshendelseFor(BehandlingId(behandlingId))) {
            sikkerLogg.warn("Fikk behandling_opprettet på behandling vi allerede har fått! Ignorer duplikat melding for: $behandlingId")
            return false
        }

        val sakId = SakId(vedtaksperiodeId)
        val behandlingskilde = behandlingskilde.avsender.behandlingskilde

        val behandling = Behandling(
            sakId = sakId,
            behandlingId = BehandlingId(behandlingId),
            relatertBehandlingId = behandlingshendelseDao.sisteBehandlingId(sakId),
            aktørId = aktørId,
            mottattTid = this.behandlingskilde.innsendt,
            registrertTid = this.behandlingskilde.registrert,
            funksjonellTid = opprettet,
            behandlingstatus = REGISTRERT,
            behandlingstype = behandlingstype.behandlingstype,
            behandlingskilde = behandlingskilde,
            behandlingsmetode = AUTOMATISK,
            hendelsesmetode = if (behandlingskilde == SAKSBEHANDLER) MANUELL else AUTOMATISK,
            yrkesaktivitetstype = yrkesaktivitetstype
        )
        return behandlingshendelseDao.lagre(behandling, this.id)
    }

    internal class Behandlingskilde(internal val innsendt: OffsetDateTime, internal val registrert: OffsetDateTime, internal val avsender: Avsender)
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
            "Søknad" -> Behandling.Behandlingstype.SØKNAD
            "Omgjøring" -> Behandling.Behandlingstype.GJENÅPNING
            "Revurdering" -> Behandling.Behandlingstype.REVURDERING
            else -> throw IllegalStateException("Kjenner ikke til behandlingstype $verdi")
        }

        private const val eventName = "behandling_opprettet"

        internal fun valider(packet: JsonMessage) {
            packet.requireVedtaksperiodeId()
            packet.requireBehandlingId()
            packet.interestedInYrkesaktivitetstype()
            packet.requireKey("fødselsnummer", "kilde.registrert", "kilde.innsendt", "kilde.avsender", "type")
        }

        internal fun opprett(packet: JsonMessage, aktørId: String) = BehandlingOpprettet(
            id = packet.hendelseId,
            data = packet.blob,
            opprettet = packet.opprettet,
            behandlingId = packet.behandlingId,
            vedtaksperiodeId = packet.vedtaksperiodeId,
            aktørId = aktørId,
            behandlingskilde = Behandlingskilde(
                innsendt = packet["kilde.innsendt"].tidspunkt,
                registrert = packet["kilde.registrert"].tidspunkt,
                avsender = Avsender(packet["kilde.avsender"].asText())
            ),
            behandlingstype = Behandlingstype(packet["type"].asText()),
            yrkesaktivitetstype = packet.yrkesaktivitetstype
        )

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao, speedClient: SpeedClient) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet -> valider(packet) },
            opprett = { packet ->
                val aktørId = retryBlocking {
                    speedClient.hentFødselsnummerOgAktørId(packet["fødselsnummer"].asText(), packet.hendelseId.toString()).getOrThrow()
                }.aktørId
                opprett(packet, aktørId)
            }
        )
    }
}
