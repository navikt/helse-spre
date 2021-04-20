package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.Month
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val spreService: SpreService,
    private val dokumentDao: DokumentDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_endret")
                message.requireKey("hendelser")
                message.requireKey("@opprettet")
                message.interestedIn("vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        if (packet["@opprettet"].asLocalDateTime() < LocalDateTime.of(2021, Month.MARCH, 19, 10, 0))
            return

        val vedtak = VedtaksperiodeEndretData(
            hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) },
        )

        if (dokumentDao.finn(vedtak.hendelser).none { it.type == Dokument.Sykmelding }) {
            log.info("Finner ikke sykmelding for vedtaksperiodeId {}", packet["vedtaksperiodeId"].asText())
            return
        }

        spreService.spre(vedtak)
        log.info("vedtaksperiode_endret lest inn")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", error.problems.toExtendedReport())
    }
}
