package no.nav.helse.spre.gosys.vedtak

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.OppdragDto.UtbetalingslinjeDto
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Utbetalingtype
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.Oppdrag
import no.nav.helse.spre.gosys.vedtakFattet.Begrunnelse
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.ANNET
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.OMREGNET_ÅRSINNTEKT
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.RAPPORTERT_ÅRSINNTEKT
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingårsak
import no.nav.helse.spre.gosys.vedtakFattet.SykepengegrunnlagsfaktaData

data class VedtakMessage(
    val utbetalingId: UUID,
    val fødselsnummer: String,
    val type: Utbetalingtype,
    private val skjæringstidspunkt: LocalDate,
    private val opprettet: LocalDateTime,
    private val fom: LocalDate,
    private val tom: LocalDate,
    val organisasjonsnummer: String,
    private val gjenståendeSykedager: Int,
    private val automatiskBehandling: Boolean,
    private val godkjentAv: String,
    private val godkjentAvEpost: String,
    private val maksdato: LocalDate?,
    private val sykepengegrunnlag: Double,
    private val sumNettobeløp: Int,
    private val sumTotalBeløp: Int,
    private val arbeidsgiverFagsystemId: String,
    private val arbeidsgiverlinjer: List<UtbetalingslinjeDto>,
    private val personFagsystemId: String,
    private val personlinjer: List<UtbetalingslinjeDto>,
    private val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaData,
    private val avvistePerioder: List<AvvistPeriode>,
    private val begrunnelser: List<Begrunnelse>?,
    private val vedtakFattetTidspunkt: LocalDateTime,
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    internal fun toVedtakPdfPayload(organisasjonsnavn: String, navn: String): VedtakPdfPayload = VedtakPdfPayload(
        sumNettoBeløp = sumNettobeløp,
        sumTotalBeløp = sumTotalBeløp,
        type = begrunnelser?.find { it.type == "DelvisInnvilgelse" || it.type == "Avslag" }?.let {
            when (it.type) {
                "DelvisInnvilgelse" -> "Delvis innvilgelse av"
                "Avslag" -> "Avslag av"
                else -> null
            }
        }
            ?: lesbarTittel(),
        linjer = arbeidsgiverlinjer.linjer(VedtakPdfPayload.MottakerType.Arbeidsgiver, "Arbeidsgiver")
            .slåSammen(personlinjer.linjer(VedtakPdfPayload.MottakerType.Person, navn.split(Regex("\\s"), 0).firstOrNull() ?: "")),
        personOppdrag = Oppdrag(personFagsystemId).takeIf { personlinjer.isNotEmpty() },
        arbeidsgiverOppdrag = Oppdrag(arbeidsgiverFagsystemId).takeIf { arbeidsgiverlinjer.isNotEmpty() },
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
        ikkeUtbetalteDager = avvistePerioder
            .map {
                val begrunnelser = if (it.begrunnelser.isNotEmpty()) {
                    mapBegrunnelser(it.begrunnelser)
                } else {
                    listOf(
                        when (it.type) {
                            "Fridag" -> "Ferie/Permisjon"
                            "Feriedag" -> "Feriedag"
                            "Permisjonsdag" -> "Permisjonsdag"
                            "Arbeidsdag" -> "Arbeidsdag"
                            else -> error("Ukjent dagtype uten begrunnelser: ${it.type}!")
                        }
                    )
                }
                IkkeUtbetalteDager(
                    fom = it.fom,
                    tom = it.tom,
                    begrunnelser = begrunnelser
                )
            },
        navn = navn,
        organisasjonsnavn = organisasjonsnavn,
        skjæringstidspunkt = skjæringstidspunkt,
        avviksprosent = sykepengegrunnlagsfakta.avviksprosent,
        skjønnsfastsettingtype = if (sykepengegrunnlagsfakta.skjønnsfastsettingårsak == Skjønnsfastsettingårsak.ANDRE_AVSNITT) when (sykepengegrunnlagsfakta.skjønnsfastsettingtype) {
            OMREGNET_ÅRSINNTEKT -> "Omregnet årsinntekt"
            RAPPORTERT_ÅRSINNTEKT -> "Rapportert årsinntekt"
            ANNET -> "Annet"
            else -> null
        } else null,
        skjønnsfastsettingårsak = when (sykepengegrunnlagsfakta.skjønnsfastsettingårsak) {
            Skjønnsfastsettingårsak.ANDRE_AVSNITT -> "Skjønnsfastsettelse ved mer enn 25 % avvik (§ 8-30 andre avsnitt)"
            Skjønnsfastsettingårsak.TREDJE_AVSNITT -> "Skjønnsfastsettelse ved mangelfull eller uriktig rapportering (§ 8-30 tredje avsnitt)"
            else -> null
        },
        arbeidsgivere = sykepengegrunnlagsfakta.arbeidsgivere,
        begrunnelser = begrunnelser?.associate {
            when (it.type) {
                "SkjønnsfastsattSykepengegrunnlagMal" -> "begrunnelseFraMal" to it.begrunnelse
                "SkjønnsfastsattSykepengegrunnlagFritekst" -> "begrunnelseFraFritekst" to it.begrunnelse
                "SkjønnsfastsattSykepengegrunnlagKonklusjon" -> "begrunnelseFraKonklusjon" to it.begrunnelse
                "DelvisInnvilgelse" -> "delvisInnvilgelse" to it.begrunnelse
                "Avslag" -> "avslag" to it.begrunnelse
                "Innvilgelse" -> "innvilgelse" to it.begrunnelse
                else -> error("Ukjent begrunnelsetype: ${it.type}")
            }
        },
        vedtakFattetTidspunkt = vedtakFattetTidspunkt
    )

    private fun List<UtbetalingslinjeDto>.linjer(mottakerType: VedtakPdfPayload.MottakerType, navn: String): List<VedtakPdfPayload.Linje> {
        return this.map {
            VedtakPdfPayload.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad,
                dagsats = it.dagsats,
                mottaker = navn,
                mottakerType = mottakerType,
                totalbeløp = it.totalbeløp,
                erOpphørt = it.erOpphørt
            )
        }
    }

    private fun lesbarTittel(): String {
        return when (this.type) {
            Utbetalingtype.UTBETALING -> "utbetaling av"
            Utbetalingtype.ETTERUTBETALING -> "etterbetaling av"
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
            "MinimumSykdomsgrad" -> "Sykdomsgrad under 20 %"
            "ManglerOpptjening" -> "Krav til 4 ukers opptjening er ikke oppfylt"
            "ManglerMedlemskap" -> "Krav til medlemskap er ikke oppfylt"
            "EtterDødsdato" -> "Personen er død"
            "Over70" -> "Personen er 70 år eller eldre"
            "AndreYtelserAap" -> "Personen mottar Aap"
            "AndreYtelserDagpenger" -> "Personen mottar Dagpenger"
            "AndreYtelserForeldrepenger" -> "Personen mottar Foreldrepenger"
            "AndreYtelserOmsorgspenger" -> "Personen mottar Omsorgspenger"
            "AndreYtelserOpplaringspenger" -> "Personen mottar Opplæringspenger"
            "AndreYtelserPleiepenger" -> "Personen mottar Pleiepenger"
            "AndreYtelserSvangerskapspenger" -> "Personen mottar Svangerskapspenger"
            else -> {
                logg.error("Ukjent begrunnelse $it")
                "Ukjent begrunnelse: \"${it}\""
            }
        }
    }

    data class AvvistPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val type: String,
        val begrunnelser: List<String>
    ) {
        constructor(fom: LocalDate, type: String, begrunnelser: List<String>) : this(
            fom = fom,
            tom = fom,
            type = type,
            begrunnelser = begrunnelser
        )
    }
}

internal fun Iterable<VedtakMessage.AvvistPeriode>.slåSammenLikePerioder(): List<VedtakMessage.AvvistPeriode> = this
    .fold(listOf()) { akkumulator, avvistPeriode ->
        val siste = akkumulator.lastOrNull()
        val nySiste = when {
            // listen er tom
            siste == null -> listOf(avvistPeriode)
            // kan utvide <siste>
            siste.kanUtvidesMed(avvistPeriode) -> listOf(siste.copy(tom = avvistPeriode.tom))
            // må legge <siste> tilbake igjen, og lage ny fordi <siste> ikke kan utvides
            else -> listOf(siste, avvistPeriode)
        }

        akkumulator.dropLast(1) + nySiste
    }

private fun VedtakMessage.AvvistPeriode.kanUtvidesMed(other: VedtakMessage.AvvistPeriode): Boolean {
    if (!etterfølgerUtenGap(this, other)) return false
    return erLiktBegrunnet(this, other) || nesteErFridagEtterArbeidsdag(this, other)
}

private fun etterfølgerUtenGap(
    sisteInnslag: VedtakMessage.AvvistPeriode,
    avvistDag: VedtakMessage.AvvistPeriode,
) = sisteInnslag.tom.plusDays(1) == avvistDag.fom

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
