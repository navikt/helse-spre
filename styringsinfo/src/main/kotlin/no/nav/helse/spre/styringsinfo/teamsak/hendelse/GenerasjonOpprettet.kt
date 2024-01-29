package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import java.time.LocalDateTime
import java.util.*

internal class GenerasjonOpprettet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val vedtaksperiodeId: UUID,
    private val generasjonId: UUID,
    private val aktørId: String,
    private val innsendt: LocalDateTime,
    private val registrert: LocalDateTime
) : Hendelse {
    override val type = "generasjon_opprettet"

    override fun håndter(behandlingDao: BehandlingDao) {
        val sakId = SakId(vedtaksperiodeId)
        val behandling = Behandling(
            sakId = sakId,
            behandlingId = BehandlingId(generasjonId),
            relatertBehandlingId = behandlingDao.forrigeBehandlingId(sakId),
            aktørId = aktørId,
            mottattTid = innsendt,
            registrertTid = registrert,
            funksjonellTid = registrert,
            tekniskTid = LocalDateTime.now(),
            behandlingStatus = Behandling.BehandlingStatus.KomplettFraBruker
        )
        behandlingDao.lagre(behandling)
    }
}