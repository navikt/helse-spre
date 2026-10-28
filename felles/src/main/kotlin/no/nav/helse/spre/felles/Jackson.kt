package no.nav.helse.spre.felles

import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

// Jacksons standard accessor-navngiving godtar ikke æ/ø/å som første tegn, og dropper da
// properties som `årsaker` og `årstall` stille ved (de)serialisering.
fun spreJacksonMapperBuilder(): JsonMapper.Builder =
    jacksonMapperBuilder()
        .accessorNaming(
            DefaultAccessorNamingStrategy
                .Provider()
                .withFirstCharAcceptance(true, true),
        )
