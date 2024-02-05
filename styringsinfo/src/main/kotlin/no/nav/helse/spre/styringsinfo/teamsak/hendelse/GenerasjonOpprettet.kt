package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.generasjonId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import java.time.LocalDateTime
import java.util.*

internal class GenerasjonOpprettet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val generasjonId: UUID,
    private val aktørId: String,
    private val generasjonkilde: Generasjonkilde,
    private val generasjonstype: Generasjonstype
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingDao: BehandlingDao) {
        val sakId = SakId(vedtaksperiodeId)
        val behandling = Behandling(
            sakId = sakId,
            behandlingId = BehandlingId(generasjonId),
            relatertBehandlingId = behandlingDao.forrigeBehandlingId(sakId),
            aktørId = aktørId,
            mottattTid = generasjonkilde.innsendt,
            registrertTid = generasjonkilde.registrert,
            funksjonellTid = generasjonkilde.registrert,
            tekniskTid = LocalDateTime.now(),
            behandlingstatus = Behandling.Behandlingstatus.Registrert,
            behandlingstype = generasjonstype.behandlingstype,
            behandlingskilde = generasjonkilde.avsender.behandlingskilde
        )
        behandlingDao.lagre(behandling)
    }

    internal class Generasjonkilde(internal val innsendt: LocalDateTime, internal val registrert: LocalDateTime, internal val avsender: Avsender)
    internal class Avsender(val verdi: String)
    internal class Generasjonstype(val verdi: String)

    internal companion object {
        private val Avsender.behandlingskilde get() = when (verdi) {
            "SYKMELDT" -> Behandling.Behandlingskilde.Sykmeldt
            "ARBEIDSGIVER" -> Behandling.Behandlingskilde.Arbeidsgiver
            "SAKSBEHANDLER" -> Behandling.Behandlingskilde.Saksbehandler
            "SYSTEM" -> Behandling.Behandlingskilde.System
            else -> throw IllegalStateException("Kjenner ikke til kildeavsender $verdi")
        }

        private val Generasjonstype.behandlingstype get() = when (verdi) {
            "Førstegangsbehandling", "TilInfotrygd" -> Behandling.Behandlingstype.Førstegangsbehandling
            "Omgjøring" -> Behandling.Behandlingstype.Omgjøring
            "Revurdering" -> Behandling.Behandlingstype.Revurdering
            else -> throw IllegalStateException("Kjenner ikke til generasjontype $verdi")
        }

        private val eventName = "generasjon_opprettet"

        internal fun river(rapidsConnection: RapidsConnection, behandlingDao: BehandlingDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            behandlingDao = behandlingDao,
            opprett = { packet ->
                packet.interestedIn("kilde.registrert", "kilde.innsendt", "kilde.avsender", "type")
                GenerasjonOpprettet(
                    id = packet.hendelseId,
                    blob = packet.blob,
                    opprettet = packet.opprettet,
                    generasjonId = packet.generasjonId,
                    vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
                    aktørId = packet["aktørId"].asText(),
                    generasjonkilde = Generasjonkilde(
                        innsendt = LocalDateTime.parse(packet["kilde.innsendt"].asText()),
                        registrert = LocalDateTime.parse(packet["kilde.registrert"].asText()),
                        avsender = Avsender(packet["kilde.avsender"].asText())
                    ),
                    generasjonstype = Generasjonstype(packet["type"].asText())
                )
            }
        )
    }
}