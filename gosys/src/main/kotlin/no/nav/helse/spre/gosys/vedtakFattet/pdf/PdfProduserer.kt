package no.nav.helse.spre.gosys.vedtakFattet.pdf

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.speed.SpeedClient
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.finnOrganisasjonsnavn
import no.nav.helse.spre.gosys.hentNavn
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.Companion.IkkeUtbetalingsdagtyper
import no.nav.helse.spre.gosys.utbetaling.Utbetaling.OppdragDto.UtbetalingslinjeDto
import no.nav.helse.spre.gosys.vedtak.AvvistPeriode
import no.nav.helse.spre.gosys.vedtak.Dekning
import no.nav.helse.spre.gosys.vedtak.PensjonsgivendeInntekt
import no.nav.helse.spre.gosys.vedtak.SNVedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.slåSammenLikePerioder
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingårsak

class PdfProduserer(
    private val pdfClient: PdfClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) {
    fun lagPdf(
        meldingOmVedtakJson: JsonNode,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate
    ): ByteArray {
        val søkernavn = hentNavn(speedClient, meldingOmVedtakJson["fødselsnummer"].asText(), UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val pdf = if (meldingOmVedtakJson["yrkesaktivitetstype"].asText() == "SELVSTENDIG") {
            runBlocking {
                pdfClient.hentSNVedtakPdf(
                    lagSNPdfPayload(
                        meldingOmVedtakJson = meldingOmVedtakJson,
                        utbetaling = utbetaling,
                        søknadsperiodeFom = søknadsperiodeFom,
                        søknadsperiodeTom = søknadsperiodeTom,
                        navn = søkernavn,
                    )
                )
            }
        } else {
            val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, utbetaling.organisasjonsnummer) }
            logg.debug("Hentet organisasjonsnavn")

            runBlocking {
                pdfClient.hentVedtakPdf(
                    lagPdfPayload(
                        meldingOmVedtakJson = meldingOmVedtakJson,
                        utbetaling = utbetaling,
                        søknadsperiodeFom = søknadsperiodeFom,
                        søknadsperiodeTom = søknadsperiodeTom,
                        navn = søkernavn,
                        organisasjonsnavn = organisasjonsnavn
                    )
                )
            }
        }
        logg.debug("Produserte pdf")
        return pdf
    }

    private fun lagPdfPayload(
        meldingOmVedtakJson: JsonNode,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate,
        navn: String,
        organisasjonsnavn: String
    ): VedtakPdfPayload = VedtakPdfPayload(
        sumNettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
        sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
        type = begrunnelseToType(meldingOmVedtakJson, utbetaling),
        linjer = utbetaling.toLinjer(navn),
        personOppdrag = utbetaling.personOppdrag.takeUnless { it.utbetalingslinjer.isEmpty() }?.tilOppdrag(),
        arbeidsgiverOppdrag = utbetaling.arbeidsgiverOppdrag.takeUnless { it.utbetalingslinjer.isEmpty() }?.tilOppdrag(),
        fødselsnummer = utbetaling.fødselsnummer,
        fom = søknadsperiodeFom,
        tom = søknadsperiodeTom,
        behandlingsdato = utbetaling.opprettet.toLocalDate(),
        organisasjonsnummer = utbetaling.organisasjonsnummer,
        dagerIgjen = utbetaling.gjenståendeSykedager,
        automatiskBehandling = utbetaling.automatiskBehandling,
        godkjentAv = utbetaling.ident,
        maksdato = utbetaling.maksdato,
        sykepengegrunnlag = meldingOmVedtakJson["sykepengegrunnlag"].asDouble(),
        ikkeUtbetalteDager = ikkeUtbetalteDager(utbetaling, meldingOmVedtakJson["skjæringstidspunkt"].asLocalDate()),
        navn = navn,
        organisasjonsnavn = organisasjonsnavn,
        skjæringstidspunkt = meldingOmVedtakJson["skjæringstidspunkt"].asLocalDate(),
        avviksprosent = meldingOmVedtakJson["sykepengegrunnlagsfakta"]["avviksprosent"]?.asDouble(),
        skjønnsfastsettingtype = if (meldingOmVedtakJson["sykepengegrunnlagsfakta"]["skjønnsfastsettingårsak"]?.let { enumValueOf<Skjønnsfastsettingårsak>(it.asText()) } == Skjønnsfastsettingårsak.ANDRE_AVSNITT) {
            meldingOmVedtakJson["sykepengegrunnlagsfakta"]["skjønnsfastsettingtype"]?.let {
                when (enumValueOf<Skjønnsfastsettingtype>(it.asText())) {
                    Skjønnsfastsettingtype.OMREGNET_ÅRSINNTEKT -> "Omregnet årsinntekt"
                    Skjønnsfastsettingtype.RAPPORTERT_ÅRSINNTEKT -> "Rapportert årsinntekt"
                    Skjønnsfastsettingtype.ANNET -> "Annet"
                }
            }
        } else {
            null
        },
        skjønnsfastsettingårsak = meldingOmVedtakJson["sykepengegrunnlagsfakta"]["skjønnsfastsettingårsak"]?.let {
            when (enumValueOf<Skjønnsfastsettingårsak>(it.asText())) {
                Skjønnsfastsettingårsak.ANDRE_AVSNITT -> "Skjønnsfastsettelse ved mer enn 25 % avvik (§ 8-30 andre avsnitt)"
                Skjønnsfastsettingårsak.TREDJE_AVSNITT -> "Skjønnsfastsettelse ved mangelfull eller uriktig rapportering (§ 8-30 tredje avsnitt)"
            }
        },
        arbeidsgivere = meldingOmVedtakJson["sykepengegrunnlagsfakta"]["arbeidsgivere"]?.map { arbeidsgiver ->
            VedtakPdfPayload.ArbeidsgiverData(
                organisasjonsnummer = arbeidsgiver["arbeidsgiver"].asText(),
                omregnetÅrsinntekt = arbeidsgiver["omregnetÅrsinntekt"].asDouble(),
                innrapportertÅrsinntekt = arbeidsgiver["innrapportertÅrsinntekt"].asDouble(),
                skjønnsfastsatt = arbeidsgiver["skjønnsfastsatt"]?.asDouble()
            )
        },
        begrunnelser = meldingOmVedtakJson.toBegrunnelser(),
        vedtakFattetTidspunkt = meldingOmVedtakJson["vedtakFattetTidspunkt"].asLocalDateTime()
    )

    private fun lagSNPdfPayload(
        meldingOmVedtakJson: JsonNode,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate,
        navn: String,
    ): SNVedtakPdfPayload = SNVedtakPdfPayload(
        sumNettoBeløp = utbetaling.arbeidsgiverOppdrag.nettoBeløp + utbetaling.personOppdrag.nettoBeløp,
        sumTotalBeløp = utbetaling.arbeidsgiverOppdrag.utbetalingslinjer.sumOf { it.totalbeløp } + utbetaling.personOppdrag.utbetalingslinjer.sumOf { it.totalbeløp },
        type = begrunnelseToType(meldingOmVedtakJson, utbetaling),
        linjer = utbetaling.toLinjer(navn),
        personOppdrag = utbetaling.personOppdrag.takeUnless { it.utbetalingslinjer.isEmpty() }?.tilOppdrag(),
        arbeidsgiverOppdrag = utbetaling.arbeidsgiverOppdrag.takeUnless { it.utbetalingslinjer.isEmpty() }?.tilOppdrag(),
        fødselsnummer = utbetaling.fødselsnummer,
        fom = søknadsperiodeFom,
        tom = søknadsperiodeTom,
        behandlingsdato = utbetaling.opprettet.toLocalDate(),
        dagerIgjen = utbetaling.gjenståendeSykedager,
        automatiskBehandling = utbetaling.automatiskBehandling,
        godkjentAv = utbetaling.ident,
        maksdato = utbetaling.maksdato,
        sykepengegrunnlag = meldingOmVedtakJson["sykepengegrunnlag"].asBigDecimal(),
        ikkeUtbetalteDager = ikkeUtbetalteDager(utbetaling = utbetaling, skjæringstidspunkt = meldingOmVedtakJson["skjæringstidspunkt"].asLocalDate()),
        navn = navn,
        skjæringstidspunkt = meldingOmVedtakJson["skjæringstidspunkt"].asLocalDate(),
        beregningsgrunnlag = meldingOmVedtakJson["sykepengegrunnlagsfakta"]["selvstendig"]["beregningsgrunnlag"].asBigDecimal(),
        pensjonsgivendeInntekter = meldingOmVedtakJson["sykepengegrunnlagsfakta"]["selvstendig"]["pensjonsgivendeInntekter"].map {
            PensjonsgivendeInntekt(
                årstall = it["årstall"].asInt(),
                beløp = it["beløp"].asBigDecimal()
            )
        },
        begrunnelser = meldingOmVedtakJson.toBegrunnelser(),
        vedtakFattetTidspunkt = meldingOmVedtakJson["vedtakFattetTidspunkt"].asLocalDateTime(),
        dekning = meldingOmVedtakJson["dekning"].toDekning()
    )

    private fun Utbetaling.toLinjer(
        navn: String
    ): List<VedtakPdfPayload.Linje> = (arbeidsgiverOppdrag.utbetalingslinjer.map { it.toLinje(navn = "Arbeidsgiver", mottakerType = VedtakPdfPayload.MottakerType.Arbeidsgiver) }
        + personOppdrag.utbetalingslinjer.map { it.toLinje(navn = navn.split(Regex("\\s"), 0).firstOrNull() ?: "", mottakerType = VedtakPdfPayload.MottakerType.Person) })
        .sortedBy { it.mottakerType }
        .sortedByDescending { it.fom }

    private fun JsonNode.toBegrunnelser(): Map<String, String>? =
        this["begrunnelser"].takeUnless { it.isMissingOrNull() }?.associate { node ->
            when (val type = node["type"].asText()) {
                "SkjønnsfastsattSykepengegrunnlagMal" -> "begrunnelseFraMal"
                "SkjønnsfastsattSykepengegrunnlagFritekst" -> "begrunnelseFraFritekst"
                "SkjønnsfastsattSykepengegrunnlagKonklusjon" -> "begrunnelseFraKonklusjon"
                "DelvisInnvilgelse" -> "delvisInnvilgelse"
                "Avslag" -> "avslag"
                "Innvilgelse" -> "innvilgelse"
                else -> error("Ukjent begrunnelsetype: $type")
            } to node["begrunnelse"].asText()
        }

    private fun JsonNode.toDekning() = Dekning(
        dekningsgrad = this["dekningsgrad"].asInt(),
        gjelderFraDag = this["gjelderFraDag"].asInt(),
    )

    private fun begrunnelseToType(
        meldingOmVedtakJson: JsonNode,
        utbetaling: Utbetaling
    ): String = meldingOmVedtakJson["begrunnelser"]
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
        Utbetaling.Utbetalingtype.ANNULLERING -> error("Forsøkte å opprette vedtaksnotat for annullering")
    }

    private fun JsonNode.asBigDecimal(): BigDecimal = BigDecimal(asText())

    private fun Utbetaling.OppdragDto.tilOppdrag(): VedtakPdfPayload.Oppdrag = VedtakPdfPayload.Oppdrag(fagsystemId)

    private fun UtbetalingslinjeDto.toLinje(
        navn: String,
        mottakerType: VedtakPdfPayload.MottakerType
    ): VedtakPdfPayload.Linje = VedtakPdfPayload.Linje(
        fom = fom,
        tom = tom,
        grad = grad,
        dagsats = dagsats,
        mottaker = navn,
        mottakerType = mottakerType,
        totalbeløp = totalbeløp,
        erOpphørt = erOpphørt
    )

    private fun ikkeUtbetalteDager(
        utbetaling: Utbetaling,
        skjæringstidspunkt: LocalDate
    ): List<VedtakPdfPayload.IkkeUtbetalteDager> = utbetaling
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
        .map { avvistPeriode ->
            val begrunnelser = avvistPeriode.begrunnelser
                .takeIf { it.isNotEmpty() }
                ?.map { begrunnelse ->
                    mapBegrunnelseTilIkkeUtbetalteDagBegrunnelse(begrunnelse)
                } ?: listOf(
                when (avvistPeriode.type) {
                    "Fridag" -> "Ferie/Permisjon"
                    "Feriedag" -> "Feriedag"
                    "Permisjonsdag" -> "Permisjonsdag"
                    "Arbeidsdag" -> "Arbeidsdag"
                    else -> error("Ukjent dagtype uten begrunnelser: ${avvistPeriode.type}!")
                }
            )
            VedtakPdfPayload.IkkeUtbetalteDager(
                fom = avvistPeriode.fom,
                tom = avvistPeriode.tom,
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
