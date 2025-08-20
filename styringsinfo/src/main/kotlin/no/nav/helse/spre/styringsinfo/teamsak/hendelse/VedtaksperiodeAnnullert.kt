package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.ANNULLERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.enhet.ManglendeEnhet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.OffsetDateTime
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.yrkesaktivitetstype

internal class VedtaksperiodeAnnullert(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    override val yrkesaktivitetstype: String,
    behandlingId: UUID
) : Hendelse {
    override val type = eventName
    private val behandlingId = BehandlingId(behandlingId)

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(behandlingId)
        val ny = builder
            .avslutt(ANNULLERT)
            .enheter(saksbehandler = ManglendeEnhet) // Vi henter ikke enhet på annuleringer
            .build(opprettet, MANUELL, yrkesaktivitetstype)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtaksperiode_annullert"

        internal fun river(
            rapidsConnection: RapidsConnection,
            hendelseDao: HendelseDao,
            behandlingshendelseDao: BehandlingshendelseDao
        ) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet ->
                packet.requireBehandlingId()
            },
            opprett = { packet -> VedtaksperiodeAnnullert(
                id = packet.hendelseId,
                data = packet.blob,
                opprettet = packet.opprettet,
                behandlingId = packet.behandlingId,
                yrkesaktivitetstype = packet.yrkesaktivitetstype,
            )}
        )
    }
}
