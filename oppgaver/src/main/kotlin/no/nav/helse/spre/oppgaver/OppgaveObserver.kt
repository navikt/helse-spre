package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class OppgaveObserver(
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
    private val rapidsConnection: RapidsConnection
) : Oppgave.Observer {
    private companion object {
        private val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
        private val LocalDateTime?.timeoutToString get() = if (this == null) "" else "Forlenger timeout med ${Duration.between(LocalDateTime.now().minusSeconds(1), this).toDays()} dager"

        /* utsettelseregler, verdi oppgitt i antall dager fra *nå* */
        private const val TimeoutLestSøknad = 110L
        private const val TimeoutVenterPåTidligereGodkjenning = 10L
        private const val TimeoutAvventerGodkjenning = 180L
        private const val TimeoutLestInntektsmelding = 60L
        private const val TimeoutLestInntektsmeldingBrukerutbetaling = 2L
    }

    override fun lagre(oppgave: Oppgave) {
        oppgaveDAO.oppdaterTilstand(oppgave)
    }

    /**
     * søknader
     */
    override fun lagOppgaveSøknad(hendelseId: UUID, dokumentId: UUID) {
        val dto = OppgaveDTO.nySøknadoppgave(dokumentId)
        sendOppgaveoppdatering("LagOppgave", hendelseId, dto, "oppgavestyring_opprett")
    }

    override fun lagOppgaveSpeilsaksbehandlereSøknad(hendelseId: UUID, dokumentId: UUID) {
        val dto = OppgaveDTO.nySøknadoppgaveSpeil(dokumentId)
        sendOppgaveoppdatering("LagOppgaveForSpeilsaksbehandlere", hendelseId, dto, "oppgavestyring_opprett_speilrelatert")
    }

    override fun ferdigbehandletSøknad(hendelseId: UUID, dokumentId: UUID) {
        ferdigstillSøknad(hendelseId, dokumentId, "oppgavestyring_ferdigbehandlet")
    }

    override fun kortSøknadFerdigbehandlet(hendelseId: UUID, dokumentId: UUID) {
        ferdigstillSøknad(hendelseId, dokumentId, "oppgavestyring_kort_periode")
    }

    private fun ferdigstillSøknad(hendelseId: UUID, dokumentId: UUID, rapidEventName: String) {
        val dto = OppgaveDTO.ferdigbehandletSøknad(dokumentId)
        sendOppgaveoppdatering("SpleisFerdigbehandlet", hendelseId, dto, rapidEventName)
    }

    override fun lestSøknad(hendelseId: UUID, dokumentId: UUID) {
        val timeout = LocalDateTime.now().plusDays(TimeoutLestSøknad)
        utsettSøknad(hendelseId, dokumentId, timeout)
    }

    override fun venterPåGodkjenningSøknad(hendelseId: UUID, dokumentId: UUID) {
        // utsetter søknaden fordi tidligere periode er til godkjenning
        utsettSøknad(hendelseId, dokumentId, LocalDateTime.now().plusDays(TimeoutVenterPåTidligereGodkjenning))
    }

    override fun avventerGodkjenningSøknad(hendelseId: UUID, dokumentId: UUID) {
        utsettSøknad(hendelseId, dokumentId, LocalDateTime.now().plusDays(TimeoutAvventerGodkjenning))
    }

    private fun utsettSøknad(hendelseId: UUID, dokumentId: UUID, timeout: LocalDateTime) {
        val dto = OppgaveDTO.utsettSøknad(dokumentId, timeout)
        sendOppgaveoppdatering("SpleisLest", hendelseId, dto, "oppgavestyring_utsatt")
    }

    /**
     * inntektsmeldinger
     */
    override fun lagOppgaveInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        val dto = OppgaveDTO.nyInntektsmeldingoppgave(dokumentId)
        sendOppgaveoppdatering("LagOppgave", hendelseId, dto, "oppgavestyring_opprett")
    }

    override fun lagOppgaveSpeilsaksbehandlereInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        val dto = OppgaveDTO.nyInntektsmeldingoppgaveSpeil(dokumentId)
        sendOppgaveoppdatering("LagOppgaveForSpeilsaksbehandlere", hendelseId, dto, "oppgavestyring_opprett_speilrelatert")
    }


    override fun ferdigbehandletInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        val dto = OppgaveDTO.ferdigbehandletInntektsmelding(dokumentId)
        sendOppgaveoppdatering("SpleisFerdigbehandlet", hendelseId, dto, "oppgavestyring_ferdigbehandlet")
    }

    override fun kortInntektsmeldingFerdigbehandlet(hendelseId: UUID, dokumentId: UUID) {
        lestInntektsmelding(hendelseId, dokumentId)
    }

    override fun lestInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        val antallDager = if (oppgaveDAO.harUtbetalingTilSøker(dokumentId)) TimeoutLestInntektsmeldingBrukerutbetaling else TimeoutLestInntektsmelding
        val timeout = LocalDateTime.now().plusDays(antallDager)
        utsettInntektsmelding(hendelseId, dokumentId, timeout)
    }

    override fun venterPåGodkjenningInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        // utsetter inntektsmelding fordi en en tidligere periode avventer godkjenning
        if (oppgaveDAO.harUtbetalingTilSøker(dokumentId)) {
            return sikkerlogg.info("Vi utsetter ikke oppgave i tilstand SpleisLest ettersom det er utbetaling til søker. {}, {}",
                keyValue("hendelseId", hendelseId),
                keyValue("dokumentId", dokumentId)
            )
        }

        utsettInntektsmelding(hendelseId, dokumentId, LocalDateTime.now().plusDays(TimeoutVenterPåTidligereGodkjenning))
    }

    override fun avventerGodkjenningInntektsmelding(hendelseId: UUID, dokumentId: UUID) {
        utsettInntektsmelding(hendelseId, dokumentId, LocalDateTime.now().plusDays(TimeoutAvventerGodkjenning))
    }

    private fun utsettInntektsmelding(hendelseId: UUID, dokumentId: UUID, timeout: LocalDateTime) {
        val dto = OppgaveDTO.utsettInntektsmelding(dokumentId, timeout)
        sendOppgaveoppdatering("SpleisLest", hendelseId, dto, "oppgavestyring_utsatt")
    }

    private fun sendOppgaveoppdatering(tilstand: String, hendelseId: UUID, dto: OppgaveDTO, rapidEventName: String) {
        publisist.publiser("${dto.dokumentId}", dto)
        rapidsConnection.publish(JsonMessage.newMessage(mapOf(
            "@event_name" to rapidEventName,
            "@id" to UUID.randomUUID(),
            "dokumentId" to dto.dokumentId,
            "hendelseId" to hendelseId
        )).toJson())
        log.info(
            "Publisert oppgave på ${dto.dokumentType.name} i tilstand: $tilstand med ider: {}, {}. ${dto.timeout.timeoutToString}. Sendes på tbd.spre-oppgaver:\n\t${objectMapper.writeValueAsString(dto)}",
            keyValue("hendelseId", hendelseId),
            keyValue("dokumentId", dto.dokumentId)
        )
    }
}
