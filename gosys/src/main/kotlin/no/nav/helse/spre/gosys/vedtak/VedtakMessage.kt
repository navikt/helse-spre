package no.nav.helse.spre.gosys.vedtak

import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Utbetalingtype
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2.Oppdrag
import no.nav.helse.spre.gosys.vedtakFattet.*
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class VedtakMessage(
    val utbetalingId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
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
    private val grunnlagForSykepengegrunnlag: Map<String, Double>,
    private val utbetaling: Utbetaling,
    private val sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaData,
    private val ikkeUtbetalteDager: List<IkkeUtbetaltDag>,
    private val begrunnelser: List<Begrunnelse>?,
    private val avslag: Avslag?,
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
        utbetaling: Utbetaling,
        sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaData,
        begrunnelser: List<Begrunnelse>?,
        avslag: Avslag?
    ) : this(
        utbetalingId = utbetaling.utbetalingId,
        opprettet = utbetaling.opprettet,
        fødselsnummer = utbetaling.fødselsnummer,
        aktørId = utbetaling.aktørId,
        skjæringstidspunkt = skjæringstidspunkt,
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
            },
        sykepengegrunnlagsfakta = sykepengegrunnlagsfakta,
        begrunnelser = begrunnelser,
        avslag = avslag,
    )

    internal fun toVedtakPdfPayloadV2(organisasjonsnavn: String, navn: String): VedtakPdfPayloadV2 = VedtakPdfPayloadV2(
        sumNettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
        sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
        type = lesbarTittel(),
        linjer = utbetaling.arbeidsgiverOppdrag.linjer(VedtakPdfPayloadV2.MottakerType.Arbeidsgiver, navn)
            .slåSammen(utbetaling.personOppdrag.linjer(VedtakPdfPayloadV2.MottakerType.Person, navn)),
        personOppdrag = personOppdrag(),
        arbeidsgiverOppdrag = arbeidsgiverOppdrag(),
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
                        "AndreYtelser" -> "Annen ytelse"
                        "Permisjonsdag" -> "Permisjonsdag"
                        "Arbeidsdag" -> "Arbeidsdag"
                        "Annullering" -> "Annullering"
                        else -> {
                            log.error("Ukjent dagtype $it")
                            "Ukjent dagtype: \"${it.type}\""
                        }
                    }
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
                else -> error("Ukjent begrunnelsetype: ${it.type}")
            }
        },
        avslagstype = avslag?.let { when (it.type) {
            Avslagstype.DELVIS_AVSLAG -> "Delvis avslag"
            Avslagstype.AVSLAG -> "Avslag"
        }},
        avslagsbegrunnelse = avslag?.begrunnelse,
    )

    private fun personOppdrag() =
        utbetaling.personOppdrag.run {
            if (utbetalingslinjer.isNotEmpty()) Oppdrag(fagsystemId = fagsystemId) else null
        }

    private fun arbeidsgiverOppdrag() =
        utbetaling.arbeidsgiverOppdrag.run {
            if (utbetalingslinjer.isNotEmpty()) Oppdrag(fagsystemId = fagsystemId) else null
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
