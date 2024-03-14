package no.nav.helse.spre.styringsinfo.datafortelling.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.datafortelling.domain.SendtSøknad
import no.nav.helse.spre.styringsinfo.toOsloOffset
import no.nav.helse.spre.styringsinfo.toOsloTid
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface SendtSøknadDaoInterface {

    fun lagre(sendtSøknad: SendtSøknad)
    fun oppdaterMelding(sendtSøknad: SendtSøknad): Int
    fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int = 1000): List<SendtSøknad>
}

class SendtSøknadDao(private val dataSource: DataSource) : SendtSøknadDaoInterface {

    override fun lagre(sendtSøknad: SendtSøknad) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO sendt_soknad (sendt, korrigerer, fom, tom, hendelse_id, melding, patch_level)
            VALUES (:sendt, :korrigerer, :fom, :tom, :hendelse_id,CAST(:melding as json), :patchLevel)
            ON CONFLICT DO NOTHING;
            """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "sendt" to sendtSøknad.sendt.toOsloOffset(),
                        "korrigerer" to sendtSøknad.korrigerer,
                        "fom" to sendtSøknad.fom,
                        "tom" to sendtSøknad.tom,
                        "hendelse_id" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding,
                        "patchLevel" to sendtSøknad.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    /**
     * @return antall rader oppdatert
     */
    override fun oppdaterMelding(sendtSøknad: SendtSøknad): Int {
        @Language("PostgreSQL")
        val query = """
            UPDATE sendt_soknad
            SET patch_level = :patchLevel, 
                melding = CAST(:melding as json)
            WHERE hendelse_id = :hendelseId
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding,
                        "patchLevel" to sendtSøknad.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<SendtSøknad> {
        @Language("PostgreSQL")
        val query = """
            SELECT sendt, korrigerer, fom, tom, hendelse_id, melding, patch_level
            FROM sendt_soknad 
            WHERE patch_level < :patchLevel
            LIMIT :antallMeldinger
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "patchLevel" to patchLevel,
                        "antallMeldinger" to antallMeldinger
                    )
                ).map { row ->
                    SendtSøknad(
                        sendt = row.zonedDateTime("sendt").toOsloTid(),
                        korrigerer = row.uuidOrNull("korrigerer"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        hendelseId = row.uuid("hendelse_id"),
                        melding = row.string("melding"),
                        patchLevel = row.int("patch_level")
                    )
                }.asList
            )
        }
    }
}

