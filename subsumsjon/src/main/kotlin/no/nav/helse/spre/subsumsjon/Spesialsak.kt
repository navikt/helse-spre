package no.nav.helse.spre.subsumsjon

import java.util.*

object Spesialsak {

    private val vedtaksperioder = Spesialsak::class.java
        .getResource("/blocklist.txt")
        ?.readText()
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()


    fun trengerIkkeSporingTilInntektsmelding(vedtaksperiodeIder: List<UUID>): Boolean {
        return vedtaksperiodeIder.any { id -> id.toString() in vedtaksperioder }
    }

}