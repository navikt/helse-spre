package no.nav.helse.spre.stonadsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal class AnnullertRiver(
    rapidsConnection: RapidsConnection,
    private val utbetaltService: UtbetaltService
) : River.PacketListener {

    private val logg: Logger = LoggerFactory.getLogger("stonadsstatistikk")
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_annullert")
                it.requireKey("fødselsnummer", "aktørId")
                it.require("tidspunkt", JsonNode::asLocalDateTime)
                it.interestedIn("arbeidsgiverFagsystemId", "personFagsystemId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val tidspunkt = packet["tidspunkt"].asLocalDateTime()

        packet["arbeidsgiverFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()?.also { arbeidsgiverFagsystemId ->
            håndterAnnullering(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                fagsystemId = arbeidsgiverFagsystemId,
                tidspunkt = tidspunkt
            )
        }
        packet["personFagsystemId"].takeUnless { it.isMissingOrNull() }?.asText()?.also { personFagsystemId ->
            håndterAnnullering(
                fødselsnummer = fødselsnummer,
                aktørId = aktørId,
                fagsystemId = personFagsystemId,
                tidspunkt = tidspunkt
            )
        }
    }

    private fun håndterAnnullering(
        fødselsnummer: String,
        aktørId: String,
        fagsystemId: String,
        tidspunkt: LocalDateTime
    ) {
        utbetaltService.håndter(Annullering(
            fødselsnummer = fødselsnummer,
            fagsystemId = fagsystemId,
            annulleringstidspunkt = tidspunkt
        ))
        logg.info("Annullering med fagsystemId=$fagsystemId på aktørId=$aktørId håndtert")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Forstod ikke utbetaling_annullert:\n" + problems.toExtendedReport())
    }
}
