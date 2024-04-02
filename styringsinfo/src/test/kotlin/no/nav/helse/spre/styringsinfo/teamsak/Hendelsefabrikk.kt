package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import java.time.OffsetDateTime
import java.util.UUID

internal class Hendelsefabrikk(
    private val sakId: SakId = nySakId(),
    private val behandlingId: BehandlingId = nyBehandlingId(),
    private val aktørId: String = "1234"
) {
    internal fun behandlingOpprettet(
        sakId: SakId = this.sakId,
        behandlingId: BehandlingId = this.behandlingId,
        aktørId: String = this.aktørId,
        behandlingstype: BehandlingOpprettet.Behandlingstype = Søknad,
        avsender: BehandlingOpprettet.Avsender = Sykmeldt,
        innsendt: OffsetDateTime = nesteTidspunkt,
        registrert: OffsetDateTime = nesteTidspunkt,
        opprettet: OffsetDateTime = nesteTidspunkt,
    ): Triple<BehandlingId, BehandlingOpprettet, SakId> {
        val behandlingskilde = BehandlingOpprettet.Behandlingskilde(innsendt, registrert, avsender)
        val behandlingOpprettet = BehandlingOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, behandlingskilde, behandlingstype)
        return Triple(behandlingId, behandlingOpprettet, sakId)
    }

    internal fun vedtakFattet(behandlingId: BehandlingId = this.behandlingId, tags: List<VedtakFattet.Companion.Tag> = listOf(VedtakFattet.Companion.Tag.Arbeidsgiverutbetaling, VedtakFattet.Companion.Tag.Innvilget), id: UUID = UUID.randomUUID()) = VedtakFattet(
        id = id,
        opprettet = nesteTidspunkt,
        data = blob,
        behandlingId = behandlingId.id,
        tags = tags
    )

    internal fun avsluttetUtenVedtak(behandlingId: BehandlingId = this.behandlingId) = AvsluttetUtenVedtak(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        behandlingId = behandlingId.id
    )

    internal fun behandlingForkastet(behandlingId: BehandlingId = this.behandlingId, hendelsesmetode: Behandling.Metode = Behandling.Metode.MANUELL) = BehandlingForkastet(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob, behandlingId = behandlingId.id,
        automatiskBehandling = hendelsesmetode == Behandling.Metode.AUTOMATISK
    )

    internal fun vedtaksperiodeEndretTilGodkjenning(sakId: SakId = this.sakId) = VedtaksperiodeEndretTilGodkjenning(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        vedtaksperiodeId = sakId.id
    )

    internal fun vedtaksperiodeEndretTilVilkårsprøving(sakId: SakId = this.sakId) = VedtaksperiodeEndretTilVilkårsprøving(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        vedtaksperiodeId = sakId.id
    )

    internal fun vedtaksperiodeGodkjent(behandlingId: BehandlingId = this.behandlingId, totrinnsbehandling: Boolean = false) = VedtaksperiodeGodkjent(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        behandlingId = behandlingId.id,
        saksbehandlerEnhet = "SB123",
        beslutterEnhet = "SB456".takeIf { totrinnsbehandling },
        automatiskBehandling = false,
        totrinnsbehandling = totrinnsbehandling
    )

    internal fun vedtaksperiodeAvvist(behandlingId: BehandlingId = this.behandlingId) = VedtaksperiodeAvvist(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        behandlingId = behandlingId.id,
        saksbehandlerEnhet = "SB123",
        automatiskBehandling = false
    )

    internal fun vedtaksperiodeAnnullert(behandlingId: BehandlingId = this.behandlingId) = VedtaksperiodeAnnullert(
        id = UUID.randomUUID(),
        opprettet = nesteTidspunkt,
        data = blob,
        behandlingId = behandlingId.id
    )

    internal companion object {
        private val nå = OffsetDateTime.now()
        private var teller = 1L
        private val nesteTidspunkt get() = nå.plusDays(teller++)

        private val objectMapper = jacksonObjectMapper()
        private val blob = objectMapper.createObjectNode()

        internal val Sykmeldt = BehandlingOpprettet.Avsender("SYKMELDT")
        internal val Arbeidsgiver = BehandlingOpprettet.Avsender("ARBEIDSGIVER")
        internal val Saksbehandler = BehandlingOpprettet.Avsender("SAKSBEHANDLER")
        internal val System = BehandlingOpprettet.Avsender("SYSTEM")

        internal val Søknad = BehandlingOpprettet.Behandlingstype("Søknad")
        internal val Omgjøring = BehandlingOpprettet.Behandlingstype("Omgjøring")
        internal val Revurdering = BehandlingOpprettet.Behandlingstype("Revurdering")

        internal fun nySakId() = SakId(UUID.randomUUID())
        internal fun nyBehandlingId() = BehandlingId(UUID.randomUUID())
    }
}