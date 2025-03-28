package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
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
import java.util.*

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
    fun `leser ikke inn vedtak_fattet-event som mangler behandlingId`() {
        testRapid.sendTestMessage(vedtakFattet(behandlingId = null))
        assertFalse(hendelseDao.harLagretHendelsen())
    }

    @Test
    fun tags() {
        testRapid.sendTestMessage(vedtakFattet())
        assertTrue(hendelseDao.harLagretHendelsen())
    }

    @Language("JSON")
    private fun vedtakFattet(
        behandlingId: UUID? = UUID.randomUUID(),
        tags: List<String> = listOf("EnArbeidsgiver", "Arbeidsgiverutbetaling", "Innvilget", "Førstegangsbehandling")
    ) = """{
      "@event_name": "vedtak_fattet",
      "@id": "${UUID.randomUUID()}",
      "@opprettet": "${LocalDateTime.now()}",
      "vedtaksperiodeId": "${UUID.randomUUID()}",
      "behandlingId": "$behandlingId",
      "tags": ${tags.map { "\"$it\"" }}
    }"""
}

internal class TestHendelseDao() : HendelseDao {

    var lagretHendelse = false

    fun harLagretHendelsen() = lagretHendelse
    fun reset() {
        lagretHendelse = false
    }

    override fun lagre(hendelse: Hendelse) {
        lagretHendelse = true
    }

}

internal class TestBehandlingshendelseDao : BehandlingshendelseDao {
    override fun initialiser(behandlingId: BehandlingId): Behandling.Builder = mockk<Behandling.Builder>(relaxed = true)
    override fun lagre(behandling: Behandling, hendelseId: UUID) = true
    override fun hent(behandlingId: BehandlingId): Behandling = throw NotImplementedError()
    override fun harLagretBehandingshendelseFor(behandlingId: BehandlingId) = true
    override fun sisteBehandlingId(sakId: SakId): BehandlingId? = null
    override fun harHåndtertHendelseTidligere(hendelseId: UUID): Boolean = false
}
