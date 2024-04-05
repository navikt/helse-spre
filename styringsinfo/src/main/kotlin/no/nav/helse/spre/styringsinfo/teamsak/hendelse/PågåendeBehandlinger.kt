package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingskilde.SAKSBEHANDLER
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet.Companion.behandlingskilde
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet.Companion.behandlingstype
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.tidspunkt
import java.time.OffsetDateTime
import java.util.UUID

internal class PågåendeBehandlinger(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    private val aktørId: String,
    private val behandlinger: List<PågåendeBehandling>
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        behandlinger
            .sortedBy { it.behandlingskilde.registrert }
            .filter { behandlingshendelseDao.hent(BehandlingId(it.behandlingId)) == null }
            .forEach { pågåendeBehandling ->
                val sakId = SakId(pågåendeBehandling.vedtaksperiodeId)
                val behandlingId = BehandlingId(pågåendeBehandling.behandlingId)
                val behandlingskilde = pågåendeBehandling.behandlingskilde.avsender.behandlingskilde

                val behandling = Behandling(
                    sakId = sakId,
                    behandlingId = behandlingId,
                    relatertBehandlingId = behandlingshendelseDao.sisteBehandlingId(sakId),
                    aktørId = aktørId,
                    mottattTid = pågåendeBehandling.behandlingskilde.innsendt,
                    registrertTid = pågåendeBehandling.behandlingskilde.registrert,
                    funksjonellTid = opprettet,
                    behandlingstatus = REGISTRERT,
                    behandlingstype = pågåendeBehandling.behandlingstype.behandlingstype,
                    behandlingskilde = behandlingskilde,
                    behandlingsmetode = AUTOMATISK,
                    hendelsesmetode = if (behandlingskilde == SAKSBEHANDLER) MANUELL else AUTOMATISK
                )
                behandlingshendelseDao.lagre(behandling, this.id)
            }
        return true
    }

    internal companion object {
        private const val eventName = "pågående_behandlinger"

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireKey("aktørId")
                packet.requireArray("behandlinger") {
                    requireVedtaksperiodeId()
                    requireBehandlingId()
                    requireKey("kilde.registrert", "kilde.innsendt", "kilde.avsender", "type")
                }
            },
            opprett = { packet -> PågåendeBehandlinger(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                aktørId = packet["aktørId"].asText(),
                behandlinger = packet["behandlinger"].map { behandling ->
                    PågåendeBehandling(
                        vedtaksperiodeId = UUID.fromString(behandling.path("vedtaksperiodeId").asText()),
                        behandlingId = UUID.fromString(behandling.path("behandlingId").asText()),
                        behandlingskilde = BehandlingOpprettet.Behandlingskilde(
                            innsendt = behandling.path("kilde").path("innsendt").tidspunkt,
                            registrert = behandling.path("kilde").path("registrert").tidspunkt,
                            avsender = BehandlingOpprettet.Avsender(behandling.path("kilde").path("avsender").asText())
                        ),
                        behandlingstype = BehandlingOpprettet.Behandlingstype(behandling.path("type").asText())
                    )
                }
            )}
        )

        internal class PågåendeBehandling(
            val vedtaksperiodeId: UUID,
            val behandlingId: UUID,
            val behandlingskilde: BehandlingOpprettet.Behandlingskilde,
            val behandlingstype: BehandlingOpprettet.Behandlingstype
        )
    }
}