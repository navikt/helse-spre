package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.KOMPLETT_FAKTAGRUNNLAG
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.OffsetDateTime
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireVedtaksperiodeId

internal data class VedtaksperioderVenterIndirektePåGodkjenning(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    val venter: List<VedtaksperiodeVenter>
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        return venter
            .filter { t -> venterPåGodkjenningOgAnnenVedtaksperiode(t) }
            .filterNot { t -> behandlingshendelseDao.hent(BehandlingId(t.behandlingId)).behandlingstatus == KOMPLETT_FAKTAGRUNNLAG }
            .map { t ->
                val builder = behandlingshendelseDao.initialiser(BehandlingId(t.behandlingId))
                val ny = builder.behandlingstatus(KOMPLETT_FAKTAGRUNNLAG).build(opprettet, AUTOMATISK) ?: return@map false
                behandlingshendelseDao.lagre(ny, id)
            }
            .any()
    }

    private fun venterPåGodkjenningOgAnnenVedtaksperiode(venter: VedtaksperiodeVenter): Boolean {
        if (venter.venterPå.venteårsak != "GODKJENNING") return false
        return venter.vedtaksperiodeId != venter.venterPå.vedtaksperiodeId
    }

    // 'vedtaksperiode_venter' sendes veldig hyppig, så for unngå å lagre alle disse hendelsene
    // når de bare sier det samme som før så ignoreres de

    internal companion object {
        private const val eventName = "vedtaksperioder_venter"

        internal fun valider(packet: JsonMessage) {
            packet.requireArray("vedtaksperioder") {
                requireBehandlingId()
                requireVedtaksperiodeId()
                require("venterPå.vedtaksperiodeId") { UUID.fromString(it.asText()) }
                requireKey("venterPå.venteårsak.hva")
            }
        }

        internal fun opprettet(packet: JsonMessage) = VedtaksperioderVenterIndirektePåGodkjenning(
            id = packet.hendelseId,
            data = packet.blob,
            opprettet = packet.opprettet,
            venter = packet["vedtaksperioder"].map { vedtaksperiodeVenter ->
                VedtaksperiodeVenter(
                    vedtaksperiodeId = UUID.fromString(vedtaksperiodeVenter.path("vedtaksperiodeId").asText()),
                    behandlingId = UUID.fromString(vedtaksperiodeVenter.path("behandlingId").asText()),
                    venterPå = VedtaksperiodeVenter.VenterPå(
                        vedtaksperiodeId = UUID.fromString(vedtaksperiodeVenter.path("venterPå").path("vedtaksperiodeId").asText()),
                        venteårsak = vedtaksperiodeVenter.path("venterPå").path("venteårsak").path("hva").asText()
                    )
                )
            }
        )

        internal fun river(
            rapidsConnection: RapidsConnection,
            hendelseDao: HendelseDao,
            behandlingshendelseDao: BehandlingshendelseDao
        ) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = ::valider,
            opprett = ::opprettet
        )
    }
}

data class VedtaksperiodeVenter(
    val vedtaksperiodeId: UUID,
    val behandlingId: UUID,
    val venterPå: VenterPå
) {
    data class VenterPå(
        val vedtaksperiodeId: UUID,
        val venteårsak: String
    )
}
