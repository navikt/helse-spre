package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Utbetalingtype
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.MottakerType
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class VedtakMessage(
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
    private val godkjentAvEpost: String,
    private val maksdato: LocalDate?,
    private val sykepengegrunnlag: Double,
    private val grunnlagForSykepengegrunnlag: Map<String, Double>,
    private val utbetaling: Utbetaling,
    private val ikkeUtbetalteDager: List<IkkeUtbetaltDag>
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    constructor(
        fom: LocalDate,
        tom: LocalDate,
        sykepengegrunnlag: Double,
        grunnlagForSykepengegrunnlag: Map<String, Double>,
        skjæringstidspunkt: LocalDate,
        utbetaling: Utbetaling
    ) : this(
        hendelseId = utbetaling.utbetalingId,
        opprettet = utbetaling.opprettet,
        fødselsnummer = utbetaling.fødselsnummer,
        aktørId = utbetaling.aktørId,
        type = utbetaling.type,
        fom = fom,
        tom = tom,
        organisasjonsnummer = utbetaling.organisasjonsnummer,
        gjenståendeSykedager = utbetaling.gjenståendeSykedager,
        automatiskBehandling = utbetaling.automatiskBehandling,
        godkjentAv = utbetaling.ident,
        godkjentAvEpost = utbetaling.epost,
        maksdato = utbetaling.maksdato,
        sykepengegrunnlag = sykepengegrunnlag,
        grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
        utbetaling = utbetaling,
        ikkeUtbetalteDager = utbetaling.ikkeUtbetalingsdager.filterNot { dag -> dag.dato.isBefore(skjæringstidspunkt) }
            .map { dag ->
                IkkeUtbetaltDag(
                    dato = dag.dato,
                    type = dag.type,
                    begrunnelser = dag.begrunnelser
                )
            }
    )

    internal fun toVedtakPdfPayloadV2() = VedtakPdfPayloadV2(
        sumNettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
        sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
        type = lesbarTittel(),
        linjer = utbetaling.arbeidsgiverOppdrag.linjer(VedtakPdfPayloadV2.MottakerType.Arbeidsgiver)
            .slåSammen(utbetaling.personOppdrag.linjer(VedtakPdfPayloadV2.MottakerType.Person)),
        personOppdrag = Oppdrag(fagsystemId = utbetaling.personOppdrag.fagsystemId),
        arbeidsgiverOppdrag = Oppdrag(fagsystemId = utbetaling.arbeidsgiverOppdrag.fagsystemId),
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
        grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
        ikkeUtbetalteDager = ikkeUtbetalteDager
            .settSammenIkkeUtbetalteDager()
            .map {
                IkkeUtbetalteDager(
                    fom = it.fom,
                    tom = it.tom,
                    begrunnelser = mapBegrunnelser(it.begrunnelser),
                    grunn = when (it.type) {
                        "AvvistDag" -> "Avvist dag"
                        "Fridag" -> "Ferie/Permisjon"
                        "Feriedag" -> "Feriedag"
                        "Permisjonsdag" -> "Permisjonsdag"
                        "Arbeidsdag" -> "Arbeidsdag"
                        "Annullering" -> "Annullering"
                        else -> {
                            log.error("Ukjent dagtype $it")
                            "Ukjent dagtype: \"${it.type}\""
                        }
                    }
                )
            }
    )


    internal fun toVedtakPdfPayload() = VedtakPdfPayload(
        fagsystemId = utbetaling.arbeidsgiverOppdrag.fagsystemId,
        totaltTilUtbetaling = utbetaling.arbeidsgiverOppdrag.nettoBeløp,
        type = lesbarTittel(),
        linjer = utbetaling.arbeidsgiverOppdrag.linjer(MottakerType.Arbeidsgiver),
        personOppdrag = VedtakPdfPayload.Oppdrag(utbetaling.personOppdrag.linjer(MottakerType.Person)),
        arbeidsgiverOppdrag = VedtakPdfPayload.Oppdrag(utbetaling.arbeidsgiverOppdrag.linjer(MottakerType.Arbeidsgiver)),
        dagsats = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.takeIf { it.isNotEmpty() }?.first()?.dagsats,
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
        grunnlagForSykepengegrunnlag = grunnlagForSykepengegrunnlag,
        ikkeUtbetalteDager = ikkeUtbetalteDager
            .settSammenIkkeUtbetalteDager()
            .map {
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = it.fom,
                    tom = it.tom,
                    begrunnelser = mapBegrunnelser(it.begrunnelser),
                    grunn = when (it.type) {
                        "AvvistDag" -> "Avvist dag"
                        "Fridag" -> "Ferie/Permisjon"
                        "Feriedag" -> "Feriedag"
                        "Permisjonsdag" -> "Permisjonsdag"
                        "Arbeidsdag" -> "Arbeidsdag"
                        "Annullering" -> "Annullering"
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
            "SykepengedagerOppbruktOver67" -> "Dager etter maksdato - Personen er mellom 67 og 70 år, jf § 8-51"
            "MinimumInntekt" -> "Krav til minste sykepengegrunnlag er ikke oppfylt"
            "MinimumInntektOver67" -> "Krav til minste sykepengegrunnlag er ikke oppfylt - Personen er mellom 67 og 70 år, jf § 8-51"
            "EgenmeldingUtenforArbeidsgiverperiode" -> "Egenmelding etter arbeidsgiverperioden"
            "MinimumSykdomsgrad" -> "Sykdomsgrad under 20%"
            "ManglerOpptjening" -> "Krav til 4 ukers opptjening er ikke oppfylt"
            "ManglerMedlemskap" -> "Krav til medlemskap er ikke oppfylt"
            "EtterDødsdato" -> "Personen er død"
            "Over70" -> "Personen er 70 år eller eldre"
            else -> {
                log.error("Ukjent begrunnelse $it")
                "Ukjent begrunnelse: \"${it}\""
            }
        }
    }

    internal data class AvvistPeriode(
        val fom: LocalDate,
        var tom: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )

    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    )
}

internal fun Iterable<VedtakMessage.IkkeUtbetaltDag>.settSammenIkkeUtbetalteDager(): List<VedtakMessage.AvvistPeriode> =
    map {
        VedtakMessage.AvvistPeriode(
            it.dato,
            it.dato,
            it.type,
            it.begrunnelser
        )
    }.fold(listOf()) { akkumulator, avvistDag ->
        val sisteInnslag = akkumulator.lastOrNull()
        if (sisteInnslag != null
            && (etterfølgerUtenGap(sisteInnslag, avvistDag))
            && (erLiktBegrunnet(sisteInnslag, avvistDag) || nesteErFridagEtterArbeidsdag(sisteInnslag, avvistDag))
        ) {
            sisteInnslag.tom = avvistDag.tom
            akkumulator
        } else akkumulator + avvistDag
    }

private fun etterfølgerUtenGap(
    sisteInnslag: VedtakMessage.AvvistPeriode,
    avvistDag: VedtakMessage.AvvistPeriode,
) = sisteInnslag.tom.plusDays(1) == avvistDag.tom

private fun nesteErFridagEtterArbeidsdag(
    sisteInnslag: VedtakMessage.AvvistPeriode,
    avvistDag: VedtakMessage.AvvistPeriode,
) = sisteInnslag.type == "Arbeidsdag" && avvistDag.type in listOf("Fridag", "Feriedag", "Permisjonsdag")

private fun erLiktBegrunnet(
    sisteInnslag: VedtakMessage.AvvistPeriode,
    avvistDag: VedtakMessage.AvvistPeriode,
) = sisteInnslag.type == avvistDag.type
        && sisteInnslag.begrunnelser.containsAll(avvistDag.begrunnelser)
        && avvistDag.begrunnelser.containsAll(sisteInnslag.begrunnelser)
