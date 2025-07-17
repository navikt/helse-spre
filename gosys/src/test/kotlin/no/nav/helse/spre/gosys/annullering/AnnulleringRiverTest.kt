package no.nav.helse.spre.gosys.annullering

import java.util.*
import kotlin.test.assertEquals
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import no.nav.helse.spre.testhelpers.januar
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class AnnulleringRiverTest : AbstractE2ETest() {

    @Test
    fun `Lagrer annullering`() {
        testRapid.sendTestMessage(annullering())

        val annulleringer = annulleringDao.finnAnnulleringHvisFinnes("fnr", "123456789")
        assertEquals(1, annulleringer.size)
        assertEquals(annulleringer.first().fom, 1.januar(2020))
        assertEquals(annulleringer.first().tom, 10.januar(2020))
    }

    @Language("JSON")
    private fun annullering(id: UUID = UUID.randomUUID(), utbetalingId: UUID = UUID.randomUUID()) = """
        {
            "@event_name": "utbetaling_annullert",
            "@opprettet": "2020-05-04T11:26:47.088455",
            "@id": "$id",
            "utbetalingId": "$utbetalingId",
            "fødselsnummer": "fnr",
            "organisasjonsnummer": "123456789",
            "tidspunkt": "2020-05-04T08:08:00.00000",
            "fom": "2020-01-01",
            "tom": "2020-01-10",
            "epost": "sara.saksbehandler@nav.no",
            "ident": "A123456",
            "arbeidsgiverFagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "personFagsystemId": "tilfeldig" 
        }
    """

}
