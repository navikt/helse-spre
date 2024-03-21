package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class VedtakFattetRiverTest {
    val hendelseDao = TestHendelseDao()
    val behandlingshendelseDao = TestBehandlingshendelseDao()
    val testRapid = TestRapid().apply {
        VedtakFattet.river(this, hendelseDao, behandlingshendelseDao)
    }

    @BeforeEach
    fun reset() {
        hendelseDao.reset()
    }

    @Test
    fun `leser inn gylding vedtak_fattet-event`() {
        testRapid.sendTestMessage(vedtakFattet())
        assertTrue(hendelseDao.harLagretHendelsen())
    }

    @Test
    fun `leser ikke inn vedtak_fattet-event som mangler utbetalingId`() {
        testRapid.sendTestMessage(vedtakFattet(utbetalingId = null))
        assertFalse(hendelseDao.harLagretHendelsen())
    }

    @Test
    fun `leser ikke inn vedtak_fattet-event som mangler sykepengegrunnlagsfakta`() {
        testRapid.sendTestMessage(vedtakFattet(sykepengegrunnlagFakta = null))
        assertFalse(hendelseDao.harLagretHendelsen())
    }

    @Test
    fun `leser ikke inn vedtak_fattet-event som mangler behandlingId`() {
        testRapid.sendTestMessage(vedtakFattet(behandlingId = null))
        assertFalse(hendelseDao.harLagretHendelsen())
    }

    @Test
    fun tags() {
        testRapid.sendTestMessage(
            vedtakFattet(
                tags = listOf(
                    "EnArbeidsgiver",
                    "Arbeidsgiverutbetaling",
                    "SykepengegrunnlagUnder2G"
                )
            )
        )
        assertTrue(hendelseDao.harLagretHendelsen())
    }

    @Language("JSON")
    private fun vedtakFattet(
        utbetalingId: UUID? = UUID.randomUUID(),
        behandlingId: UUID? = UUID.randomUUID(),
        sykepengegrunnlagFakta: String? = """{ "fastsatt": "EtterHovedregel" }""",
        tags: List<String> = emptyList()
    ) = """{
      "@event_name": "vedtak_fattet",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "utbetalingId": "${utbetalingId}",
      "behandlingId": "${behandlingId}",
      "sykepengegrunnlagsfakta": $sykepengegrunnlagFakta,
      "tags": ${tags.map { "\"$it\"" }}
    }"""
}

internal class TestHendelseDao() : HendelseDao {

    var lagretHendelse = false

    fun harLagretHendelsen() = lagretHendelse
    fun reset() {
        lagretHendelse = false
    }

    override fun lagre(hendelse: Hendelse): Boolean {
        lagretHendelse = true
        return true
    }

}

internal class TestBehandlingshendelseDao : BehandlingshendelseDao {
    override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? = null
    override fun initialiser(sakId: SakId): Behandling.Builder? = null
    override fun lagre(behandling: Behandling, hendelseId: UUID) = true
    override fun hent(behandlingId: BehandlingId): Behandling? = null
    override fun sisteBehandlingId(sakId: SakId): BehandlingId? = null
    override fun erFørstegangsbehandling(sakId: SakId): Boolean = true
}