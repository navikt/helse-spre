package no.nav.helse.spre.stonadsstatistikk

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.stonadsstatistikk.DatabaseHelpers.Companion.dataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class NyttDokumentRiverTest {
    val testRapid = TestRapid()
    val dokumentDao = DokumentDao(dataSource)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skriver dokumenter til hendelse`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)

        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))

        val dokumenter = dokumentDao.finnDokumenter(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
    }

    @Test
    fun `håndterer duplikate dokumenter`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)
        val inntektsmelding = Hendelse(UUID.randomUUID(), UUID.randomUUID(), Dokument.Inntektsmelding)

        testRapid.sendTestMessage(sendtSøknadArbeidsgiverMessage(sykmelding, søknad))
        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(inntektsmeldingMessage(inntektsmelding))

        val dokumenter = dokumentDao.finnDokumenter(listOf(sykmelding.hendelseId, søknad.hendelseId, inntektsmelding.hendelseId))
        assertEquals(Dokumenter(sykmelding, søknad, inntektsmelding), dokumenter)
    }

    private fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${sykmelding.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

    private fun sendtSøknadArbeidsgiverMessage(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_arbeidsgiver",
            "@id": "${sykmelding.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

    private fun inntektsmeldingMessage(hendelse: Hendelse) =
        """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""
}
