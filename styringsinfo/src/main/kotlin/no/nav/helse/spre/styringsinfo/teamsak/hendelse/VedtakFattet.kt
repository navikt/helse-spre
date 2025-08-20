package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.behandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.blob
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.hendelseId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.opprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.requireBehandlingId
import java.time.OffsetDateTime
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseRiver.Companion.yrkesaktivitetstype

internal class VedtakFattet(
    override val id: UUID,
    override val opprettet: OffsetDateTime,
    override val data: JsonNode,
    override val yrkesaktivitetstype: String,
    private val behandlingId: UUID,
    private val tags: Tags
) : Hendelse {
    override val type = eventName

    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean {
        val builder = behandlingshendelseDao.initialiser(BehandlingId(behandlingId))
        val ny = builder
            .mottaker(tags.mottaker)
            .avslutt(tags.behandlingsresultat)
            .periodetype(tags.periodetype)
            .build(opprettet, AUTOMATISK, yrkesaktivitetstype)
            ?: return false
        return behandlingshendelseDao.lagre(ny, this.id)
    }

    internal companion object {
        private const val eventName = "vedtak_fattet"

        internal fun valider(packet: JsonMessage) {
            packet.requireBehandlingId()
            packet.requireTags()
        }

        internal fun opprett(packet: JsonMessage) = VedtakFattet(
            id = packet.hendelseId,
            opprettet = packet.opprettet,
            data = packet.blob,
            behandlingId = packet.behandlingId,
            tags = Tags(packet.tags),
            yrkesaktivitetstype = packet.yrkesaktivitetstype
        )

        internal fun river(rapidsConnection: RapidsConnection, hendelseDao: HendelseDao, behandlingshendelseDao: BehandlingshendelseDao) = HendelseRiver(
            eventName = eventName,
            rapidsConnection = rapidsConnection,
            hendelseDao = hendelseDao,
            behandlingshendelseDao = behandlingshendelseDao,
            valider = { packet -> valider(packet) },
            opprett = { packet -> opprett(packet) }
        )

        private val JsonMessage.tags get() = this["tags"].map { it.asText() }
        private fun JsonMessage.requireTags() = requireKey("tags")
    }
}
