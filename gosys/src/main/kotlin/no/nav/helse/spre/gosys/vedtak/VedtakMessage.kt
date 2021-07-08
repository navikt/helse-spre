package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.gosys.io.IO
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Utbetalingtype
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

data class VedtakMessage private constructor(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    val type: Utbetalingtype,
    private val opprettet: LocalDateTime,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val organisasjonsnummer: String,
    private val gjenståendeSykedager: Int,
    private val automatiskBehandling: Boolean,
    private val godkjentAv: String,
    private val maksdato: LocalDate?,
    private val sykepengegrunnlag: Double,
    private val utbetaling: Utbetaling,
    private val ikkeUtbetalteDager: List<IkkeUtbetaltDag>
) {

    companion object {
        fun fraVedtakOgUtbetaling(
            vedtak: VedtakFattetData,
            utbetaling: no.nav.helse.spre.gosys.utbetaling.Utbetaling
        ): VedtakMessage {
            check(utbetaling.fødselsnummer == vedtak.fødselsnummer) {
                "Alvorlig feil: Vedtaket peker på utbetaling med et annet fødselnummer"
            }
            return VedtakMessage(vedtak, utbetaling)
        }
    }

    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    constructor(vedtak: VedtakFattetData, utbetaling: no.nav.helse.spre.gosys.utbetaling.Utbetaling) :
            this(
                hendelseId = vedtak.id,
                opprettet = vedtak.opprettet,
                fødselsnummer = vedtak.fødselsnummer,
                aktørId = vedtak.aktørId,
                type = utbetaling.type,
                fom = vedtak.fom,
                tom = vedtak.tom,
                organisasjonsnummer = utbetaling.organisasjonsnummer,
                gjenståendeSykedager = utbetaling.gjenståendeSykedager,
                automatiskBehandling = utbetaling.automatiskBehandling,
                godkjentAv = utbetaling.ident,
                maksdato = utbetaling.maksdato,
                sykepengegrunnlag = vedtak.sykepengegrunnlag,
                utbetaling = utbetaling.arbeidsgiverOppdrag.takeIf { it.fagområde == "SPREF" }!!.let { oppdrag ->
                    Utbetaling(
                        fagområde = Utbetaling.Fagområde.SPREF,
                        fagsystemId = oppdrag.fagsystemId,
                        totalbeløp = oppdrag.nettoBeløp,
                        utbetalingslinjer = oppdrag.utbetalingslinjer.map { utbetalingslinje ->
                            Utbetaling.Utbetalingslinje(
                                dagsats = utbetalingslinje.dagsats,
                                fom = utbetalingslinje.fom,
                                tom = utbetalingslinje.tom,
                                grad = utbetalingslinje.grad.toInt(),
                                beløp = utbetalingslinje.dagsats,
                                mottaker = "arbeidsgiver"
                            )
                        }
                    )
                },
                ikkeUtbetalteDager = utbetaling.ikkeUtbetalingsdager.filterNot { dag -> dag.dato.isBefore(vedtak.skjæringstidspunkt) }
                    .map { dag ->
                        IkkeUtbetaltDag(
                            dato = dag.dato,
                            type = dag.type,
                            begrunnelser = dag.begrunnelser
                        )
                    }
            )

    constructor(vedtak: IO.Vedtak) :
            this(
                hendelseId = vedtak.`@id`,
                opprettet = vedtak.`@opprettet`,
                fødselsnummer = vedtak.fødselsnummer,
                aktørId = vedtak.aktørId,
                type = vedtak.utbetalingtype,
                fom = vedtak.fom,
                tom = vedtak.tom,
                organisasjonsnummer = vedtak.organisasjonsnummer,
                gjenståendeSykedager = vedtak.gjenståendeSykedager,
                automatiskBehandling = vedtak.automatiskBehandling,
                godkjentAv = vedtak.godkjentAv,
                maksdato = vedtak.maksdato,
                sykepengegrunnlag = vedtak.sykepengegrunnlag,
                utbetaling = Utbetaling(vedtak.utbetalt.find { it.fagområde == IO.Fagområde.SPREF }!!),
                ikkeUtbetalteDager = vedtak.ikkeUtbetalteDager.map { IkkeUtbetaltDag(it) }
            )

    internal fun toVedtakPdfPayload() = VedtakPdfPayload(
        fagsystemId = utbetaling.fagsystemId,
        totaltTilUtbetaling = utbetaling.totalbeløp,
        type = lesbarTittel(),
        linjer = utbetaling.utbetalingslinjer.map {
            VedtakPdfPayload.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad,
                beløp = it.beløp,
                mottaker = it.mottaker
            )
        },
        dagsats = utbetaling.utbetalingslinjer.takeIf { it.isNotEmpty() }?.first()?.dagsats,
        fødselsnummer = fødselsnummer,
        fom = fom,
        tom = tom,
        behandlingsdato = opprettet.toLocalDate(),
        organisasjonsnummer = organisasjonsnummer,
        dagerIgjen = gjenståendeSykedager,
        automatiskBehandling = automatiskBehandling,
        godkjentAv = godkjentAv,
        maksdato = maksdato,
        sykepengegrunnlag = sykepengegrunnlag,
        ikkeUtbetalteDager = ikkeUtbetalteDager
            .settSammenIkkeUtbetalteDager()
            .map {
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = it.fom,
                    tom = it.tom,
                    begrunnelser = mapBegrunnelser(it.begrunnelser),
                    grunn = when (it.type) {
                        "AvvistDag" -> "Avvist dag"
                        "SykepengedagerOppbrukt" -> "Dager etter maksdato"
                        "MinimumInntekt" -> "Krav til minste sykepengegrunnlag er ikke oppfylt"
                        "EgenmeldingUtenforArbeidsgiverperiode" -> "Egenmelding etter arbeidsgiverperioden"
                        "MinimumSykdomsgrad" -> "Sykdomsgrad under 20%"
                        "Fridag" -> "Ferie/Permisjon"
                        "Arbeidsdag" -> "Arbeidsdag"
                        "EtterDødsdato" -> "Personen er død"
                        else -> {
                            log.error("Ukjent dagtype $it")
                            "Ukjent dagtype: \"${it.type}\""
                        }
                    }
                )
            }
    )

    private fun lesbarTittel(): String {
        return when (this.type) {
            Utbetalingtype.UTBETALING -> "utbetalt"
            Utbetalingtype.ETTERUTBETALING -> "etterutbetaling av"
            Utbetalingtype.REVURDERING -> "revurdering av"
            Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }


    private fun mapBegrunnelser(begrunnelser: List<String>): List<String> = begrunnelser.map {
        when (it) {
            "SykepengedagerOppbrukt" -> "Dager etter maksdato"
            "MinimumInntekt" -> "Krav til minste sykepengegrunnlag er ikke oppfylt"
            "EgenmeldingUtenforArbeidsgiverperiode" -> "Egenmelding etter arbeidsgiverperioden"
            "MinimumSykdomsgrad" -> "Sykdomsgrad under 20%"
            "ManglerOpptjening" -> "Krav til 4 ukers opptjening er ikke oppfylt"
            "ManglerMedlemskap" -> "Krav til medlemskap er ikke oppfylt"
            "EtterDødsdato" -> "Personen er død"
            else -> {
                log.error("Ukjent begrunnelse $it")
                "Ukjent begrunnelse: \"${it}\""
            }
        }
    }

    internal data class DagAcc(
        val fom: LocalDate,
        var tom: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )

    data class Utbetaling(
        val fagområde: Fagområde,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    ) {
        constructor(rådata: IO.Utbetaling) :
                this(
                    fagområde = Fagområde.valueOf(rådata.fagområde),
                    fagsystemId = rådata.fagsystemId,
                    totalbeløp = rådata.totalbeløp,
                    utbetalingslinjer = rådata.utbetalingslinjer.map { Utbetalingslinje(it) }
                )

        enum class Fagområde {
            SPREF;

            companion object {
                fun valueOf(fagområde: IO.Fagområde): Fagområde {
                    if (fagområde == IO.Fagområde.SPREF) return SPREF
                    throw RuntimeException("Fagområde $fagområde finnes ikke.")
                }
            }
        }

        data class Utbetalingslinje(
            val dagsats: Int,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Int,
            val beløp: Int,
            val mottaker: String
        ) {
            constructor(rådata: IO.Utbetalingslinje) :
                    this(
                        dagsats = rådata.dagsats,
                        fom = rådata.fom,
                        tom = rådata.tom,
                        grad = rådata.grad.roundToInt(),
                        beløp = rådata.beløp,
                        mottaker = "arbeidsgiver"
                    )
        }
    }

    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    ) {
        constructor(rådata: IO.IkkeUtbetaltDag) :
                this(
                    dato = rådata.dato,
                    type = rådata.type,
                    begrunnelser = rådata.begrunnelser ?: emptyList()
                )
    }
}

internal fun Iterable<VedtakMessage.IkkeUtbetaltDag>.settSammenIkkeUtbetalteDager(): List<VedtakMessage.DagAcc> =
    map { VedtakMessage.DagAcc(it.dato, it.dato, it.type, it.begrunnelser) }.fold(listOf()) { akkumulator, avvistDag ->
        val sisteInnslag = akkumulator.lastOrNull()
        if (sisteInnslag != null && ((sisteInnslag.type == avvistDag.type &&
                    sisteInnslag.begrunnelser.containsAll(avvistDag.begrunnelser) && avvistDag.begrunnelser.containsAll(
                sisteInnslag.begrunnelser
            )
                    ) || (sisteInnslag.type == "Arbeidsdag") && avvistDag.type == "Fridag")
            && sisteInnslag.tom.plusDays(1) == avvistDag.tom
        ) {
            sisteInnslag.tom = avvistDag.tom
            return@fold akkumulator
        }
        akkumulator + avvistDag
    }
