package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling

internal enum class Tag {
    IngenUtbetaling,
    NegativPersonutbetaling,
    Personutbetaling,
    Arbeidsgiverutbetaling,
    NegativArbeidsgiverutbetaling,
    Innvilget,
    DelvisInnvilget,
    Avslag,
    EnArbeidsgiver,
    Grunnbeløpsregulering,
    FlereArbeidsgivere,
    `6GBegrenset`,
    SykepengegrunnlagUnder2G,
    IngenNyArbeidsgiverperiode,
    Førstegangsbehandling,
    Forlengelse,
    InngangsvilkårFraInfotrygd,
    TilkommenInntekt
}

internal class Tags(private val tags: Set<Tag>) {
    internal constructor(tags: List<String>) :this(tags.tilKjenteTags())

    internal val periodetype get(): Behandling.Periodetype {
        if (tags.contains(Tag.Førstegangsbehandling)) return Behandling.Periodetype.FØRSTEGANGSBEHANDLING
        if (tags.contains(Tag.Forlengelse)) return Behandling.Periodetype.FORLENGELSE
        throw UtledingFraTagsException("periodetype", tags)
    }

    internal val mottaker get(): Behandling.Mottaker {
        val sykmeldtErMottaker = tags.any { it in listOf(Tag.Personutbetaling, Tag.NegativPersonutbetaling) }
        val arbeidsgiverErMottaker = tags.any { it in listOf(Tag.Arbeidsgiverutbetaling, Tag.NegativArbeidsgiverutbetaling) }
        val ingenErMottaker = tags.contains(Tag.IngenUtbetaling)
        return when {
            ingenErMottaker -> Behandling.Mottaker.INGEN
            sykmeldtErMottaker && arbeidsgiverErMottaker -> Behandling.Mottaker.SYKMELDT_OG_ARBEIDSGIVER
            sykmeldtErMottaker -> Behandling.Mottaker.SYKMELDT
            arbeidsgiverErMottaker -> Behandling.Mottaker.ARBEIDSGIVER
            else -> throw UtledingFraTagsException("mottaker", tags)
        }
    }

    internal val behandlingsresultat get(): Behandling.Behandlingsresultat {
        return when {
            tags.any { it == Tag.Innvilget } -> Behandling.Behandlingsresultat.INNVILGET
            tags.any { it == Tag.DelvisInnvilget } -> Behandling.Behandlingsresultat.DELVIS_INNVILGET
            tags.any { it == Tag.Avslag } -> Behandling.Behandlingsresultat.AVSLAG
            else -> throw UtledingFraTagsException("behandlingsresultat", tags)
        }
    }

    private companion object {
        private fun valueOfOrNull(name: String) = Tag.entries.firstOrNull { it.name == name }

        private fun List<String>.tilKjenteTags(): Set<Tag> {
            return mapNotNull { streng -> valueOfOrNull(streng) }.toSet()
        }

        class UtledingFraTagsException(felt: String, tags: Set<Tag>): IllegalStateException("Nå kom det jaggu et event med en $felt jeg ikke klarte å tolke. Dette må være en feil. Ta en titt! Tagger: $tags")
    }
}
