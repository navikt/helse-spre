package no.nav.helse.spre.styringsinfo.teamsak.hendelse

internal interface HendelseDao {

    fun lagre(hendelse: Hendelse)
}