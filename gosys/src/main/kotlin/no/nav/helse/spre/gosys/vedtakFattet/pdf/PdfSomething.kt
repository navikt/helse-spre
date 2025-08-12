package no.nav.helse.spre.gosys.vedtakFattet.pdf

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import java.time.LocalDate
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Companion.IkkeUtbetalingsdagtyper
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.OppdragDto.UtbetalingslinjeDto
import no.nav.helse.spre.gosys.vedtak.AvvistPeriode
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.Oppdrag
import no.nav.helse.spre.gosys.vedtak.slåSammen
import no.nav.helse.spre.gosys.vedtak.slåSammenLikePerioder
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingårsak

object PdfSomething {

    fun lagPdfPayload(
        packet: JsonMessage,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate,
        navn: String,
        organisasjonsnavn: String
    ): VedtakPdfPayload = VedtakPdfPayload(
        sumNettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
        sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
        type = packet["begrunnelser"]
            .takeUnless { it.isMissingOrNull() }
            ?.map { begrunnelse -> begrunnelse["type"].asText() }
            ?.find { it == "DelvisInnvilgelse" || it == "Avslag" }
            ?.let {
                when (it) {
                    "DelvisInnvilgelse" -> "Delvis innvilgelse av"
                    "Avslag" -> "Avslag av"
                    else -> null
                }
            } ?: when (utbetaling.type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "utbetaling av"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "etterbetaling av"
            Utbetaling.Utbetalingtype.REVURDERING -> "revurdering av"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        },
        linjer = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.linjer(VedtakPdfPayload.MottakerType.Arbeidsgiver, "Arbeidsgiver")
            .slåSammen(utbetaling.personOppdrag.utbetalingslinjer.linjer(VedtakPdfPayload.MottakerType.Person, navn.split(Regex("\\s"), 0).firstOrNull() ?: "")),
        personOppdrag = Oppdrag(utbetaling.personOppdrag.fagsystemId).takeIf { utbetaling.personOppdrag.utbetalingslinjer.isNotEmpty() },
        arbeidsgiverOppdrag = Oppdrag(utbetaling.arbeidsgiverOppdrag.fagsystemId).takeIf { utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.isNotEmpty() },
        fødselsnummer = utbetaling.fødselsnummer,
        fom = søknadsperiodeFom,
        tom = søknadsperiodeTom,
        behandlingsdato = utbetaling.opprettet.toLocalDate(),
        organisasjonsnummer = utbetaling.organisasjonsnummer,
        dagerIgjen = utbetaling.gjenståendeSykedager,
        automatiskBehandling = utbetaling.automatiskBehandling,
        godkjentAv = utbetaling.ident,
        maksdato = utbetaling.maksdato,
        sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
        ikkeUtbetalteDager = ikkeUtbetalteDager(utbetaling, packet["skjæringstidspunkt"].asLocalDate()),
        navn = navn,
        organisasjonsnavn = organisasjonsnavn,
        skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
        avviksprosent = packet["sykepengegrunnlagsfakta"]["avviksprosent"]?.asDouble(),
        skjønnsfastsettingtype = if (packet["sykepengegrunnlagsfakta"]["skjønnsfastsettingårsak"]?.let { enumValueOf<Skjønnsfastsettingårsak>(it.asText()) } == Skjønnsfastsettingårsak.ANDRE_AVSNITT) {
            packet["sykepengegrunnlagsfakta"]["skjønnsfastsettingtype"]?.let {
                when (enumValueOf<Skjønnsfastsettingtype>(it.asText())) {
                    Skjønnsfastsettingtype.OMREGNET_ÅRSINNTEKT -> "Omregnet årsinntekt"
                    Skjønnsfastsettingtype.RAPPORTERT_ÅRSINNTEKT -> "Rapportert årsinntekt"
                    Skjønnsfastsettingtype.ANNET -> "Annet"
                }
            }
        } else {
            null
        },
        skjønnsfastsettingårsak = packet["sykepengegrunnlagsfakta"]["skjønnsfastsettingårsak"]?.let {
            when (enumValueOf<Skjønnsfastsettingårsak>(it.asText())) {
                Skjønnsfastsettingårsak.ANDRE_AVSNITT -> "Skjønnsfastsettelse ved mer enn 25 % avvik (§ 8-30 andre avsnitt)"
                Skjønnsfastsettingårsak.TREDJE_AVSNITT -> "Skjønnsfastsettelse ved mangelfull eller uriktig rapportering (§ 8-30 tredje avsnitt)"
            }
        },
        arbeidsgivere = packet["sykepengegrunnlagsfakta"]["arbeidsgivere"]?.map { arbeidsgiver ->
            VedtakPdfPayload.ArbeidsgiverData(
                organisasjonsnummer = arbeidsgiver["arbeidsgiver"].asText(),
                omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                innrapportertÅrsinntekt = arbeidsgiver["innrapportertÅrsinntekt"].asDouble(),
                skjønnsfastsatt = arbeidsgiver["skjønnsfastsatt"]?.asDouble()
            )
        },
        begrunnelser = packet["begrunnelser"].takeUnless { it.isMissingOrNull() }?.associate { node ->
            val begrunnelse = node["begrunnelse"].asText()
            when (val type = node["type"].asText()) {
                "SkjønnsfastsattSykepengegrunnlagMal" -> "begrunnelseFraMal" to begrunnelse
                "SkjønnsfastsattSykepengegrunnlagFritekst" -> "begrunnelseFraFritekst" to begrunnelse
                "SkjønnsfastsattSykepengegrunnlagKonklusjon" -> "begrunnelseFraKonklusjon" to begrunnelse
                "DelvisInnvilgelse" -> "delvisInnvilgelse" to begrunnelse
                "Avslag" -> "avslag" to begrunnelse
                "Innvilgelse" -> "innvilgelse" to begrunnelse
                else -> error("Ukjent begrunnelsetype: $type")
            }
        },
        vedtakFattetTidspunkt = packet["vedtakFattetTidspunkt"].asLocalDateTime()
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

    private fun ikkeUtbetalteDager(
        utbetaling: Utbetaling,
        skjæringstidspunkt: LocalDate
    ): List<IkkeUtbetalteDager> = utbetaling
        .utbetalingsdager
        .filter { it.type in IkkeUtbetalingsdagtyper }
        .filterNot { dag -> dag.dato < skjæringstidspunkt }
        .map { dag ->
            AvvistPeriode(
                fom = dag.dato,
                tom = dag.dato,
                type = dag.type,
                begrunnelser = dag.begrunnelser
            )
        }
        .slåSammenLikePerioder()
        .map {
            val begrunnelser = it.begrunnelser
                .takeIf { it.isNotEmpty() }
                ?.map { begrunnelse ->
                    mapBegrunnelseTilIkkeUtbetalteDagBegrunnelse(begrunnelse)
                } ?: listOf(
                when (it.type) {
                    "Fridag" -> "Ferie/Permisjon"
                    "Feriedag" -> "Feriedag"
                    "Permisjonsdag" -> "Permisjonsdag"
                    "Arbeidsdag" -> "Arbeidsdag"
                    else -> error("Ukjent dagtype uten begrunnelser: ${it.type}!")
                }
            )
            IkkeUtbetalteDager(
                fom = it.fom,
                tom = it.tom,
                begrunnelser = begrunnelser
            )
        }

    private fun mapBegrunnelseTilIkkeUtbetalteDagBegrunnelse(begrunnelse: String): String = when (begrunnelse) {
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
            logg.error("Ukjent begrunnelse $begrunnelse")
            "Ukjent begrunnelse: \"${begrunnelse}\""
        }
    }
}
