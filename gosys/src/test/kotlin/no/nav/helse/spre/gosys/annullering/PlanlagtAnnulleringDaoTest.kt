package no.nav.helse.spre.gosys.annullering

import com.github.navikt.tbd_libs.test_support.TestDataSource
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import no.nav.helse.spre.gosys.databaseContainer
import no.nav.helse.spre.testhelpers.januar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PlanlagtAnnulleringDaoTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
    }

    @Test
    fun `dao-test`() {
        val vedtaksperiode1 = UUID.randomUUID()
        val vedtaksperiode2 = UUID.randomUUID()
        val hendelseIdPlan = UUID.randomUUID()
        val planlagtAnnulleringMessage = PlanlagtAnnulleringMessage(
            hendelseId = hendelseIdPlan,
            fødselsnummer = "1",
            yrkesaktivitet = "2",
            fom = 1.januar,
            tom = 31.januar,
            saksbehandlerIdent = "3",
            årsaker = listOf("Annet", "Yrkesskade"),
            begrunnelse = "begrunnelse",
            vedtaksperioder = listOf(vedtaksperiode1, vedtaksperiode2),
            opprettet = LocalDateTime.now()
        )

        val dao = PlanlagtAnnulleringDao(dataSource.ds)
        dao.lagre(planlagtAnnulleringMessage)

        val planIder = dao.settVedtaksperiodeAnnullert(vedtaksperiode1)
        assertEquals(listOf(hendelseIdPlan), planIder)

        val plan = dao.finnPlanlagtAnnullering(hendelseIdPlan)!!
        assertEquals(listOf("Annet", "Yrkesskade"), plan.årsaker)
        assertEquals(2, plan.vedtaksperioder.size)
        assertFalse(plan.erFerdigAnnullert())

        dao.settVedtaksperiodeAnnullert(vedtaksperiode2)
        val oppdatertPlan = dao.finnPlanlagtAnnullering(hendelseIdPlan)!!
        assertTrue(oppdatertPlan.erFerdigAnnullert())

        dao.settNotatOpprettet(hendelseIdPlan)
        val ferdigAnnullertPlan = dao.finnPlanlagtAnnullering(hendelseIdPlan)!!
        assertTrue(ferdigAnnullertPlan.erNotatOpprettet())
    }
}
