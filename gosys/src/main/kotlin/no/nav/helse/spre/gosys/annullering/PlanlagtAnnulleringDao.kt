package no.nav.helse.spre.gosys.annullering

import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class PlanlagtAnnulleringDao(private val dataSource: DataSource) {

    fun lagre(plan: PlanlagtAnnulleringMessage) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO planlagt_annullering(id, fnr, yrkesaktivitetstype, organisasjonsnummer, fom, tom, saksbehandler_ident, arsaker, begrunnelse, opprettet)
            values(:id, :fnr, :yrkesaktivitetstype, :organisasjonsnummer, :fom, :tom, :saksbehandler_ident, :arsaker, :begrunnelse, :opprettet) ON CONFLICT DO NOTHING"""

        @Language("PostgreSQL")
        val insertVedtaksperioder = """
            INSERT INTO vedtaksperioder_som_skal_annulleres(vedtaksperiode_id, plan)
            values(:vedtaksperiode_id, :plan) ON CONFLICT DO NOTHING
        """

        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "id" to plan.hendelseId,
                        "fnr" to plan.fødselsnummer,
                        "yrkesaktivitetstype" to plan.yrkesaktivitetstype,
                        "organisasjonsnummer" to plan.organisasjonsnummer,
                        "fom" to plan.fom,
                        "tom" to plan.tom,
                        "saksbehandler_ident" to plan.saksbehandlerIdent,
                        "arsaker" to plan.årsaker.joinToString { it },
                        "begrunnelse" to plan.begrunnelse,
                        "opprettet" to plan.opprettet
                    )
                ).asExecute
            )
            plan.vedtaksperioder.forEach { vedtaksperiode ->
                session.run(
                    queryOf(
                        insertVedtaksperioder,
                        mapOf(
                            "vedtaksperiode_id" to vedtaksperiode,
                            "plan" to plan.hendelseId
                        )
                    ).asExecute
                )
            }
        }
    }

    fun settVedtaksperiodeAnnullert(vedtaksperiodeId: UUID): List<UUID> {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "UPDATE vedtaksperioder_som_skal_annulleres SET annullert = now() WHERE vedtaksperiode_id = ? AND annullert is null returning plan"
            return session.run(
                queryOf(
                    query,
                    vedtaksperiodeId
                ).map { it.uuid("plan") }.asList
            )
        }
    }

    fun settNotatOpprettet(planId: UUID): Int {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "UPDATE planlagt_annullering SET notat_opprettet = now() WHERE id = ?"
            return session.run(
                queryOf(
                    query,
                    planId
                ).asUpdate
            )
        }

    }

    fun finnPlanlagtAnnullering(planId: UUID): PlanlagtAnnullering? {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT * FROM planlagt_annullering WHERE id = ?"
            @Language("PostgreSQL")
            val vedtaksperioderForPlan = "SELECT vedtaksperiode_id, annullert FROM vedtaksperioder_som_skal_annulleres WHERE plan = ?"
            val vedtaksperioder = session.run(
                queryOf(vedtaksperioderForPlan, planId).map {
                    PlanlagtAnnullering.Vedtaksperiode(
                        vedtaksperiodeId = it.uuid("vedtaksperiode_id"),
                        annullert = it.localDateTimeOrNull("annullert")
                    )
                }.asList
            )
            return session.run(
                queryOf(query, planId).map { PlanlagtAnnullering(
                    id = it.uuid("id"),
                    fnr = it.string("fnr"),
                    yrkesaktivitetstype = it.string("yrkesaktivitetstype"),
                    organisasjonsnummer = it.string("organisasjonsnummer"),
                    fom = it.localDate("fom"),
                    tom = it.localDate("tom"),
                    saksbehandlerIdent = it.string("saksbehandler_ident"),
                    årsaker = it.string("arsaker").split(", "),
                    begrunnelse = it.string("begrunnelse"),
                    vedtaksperioder = vedtaksperioder,
                    notat_opprettet = it.localDateTimeOrNull("notat_opprettet"),
                    opprettet = it.localDateTime("opprettet")
                ) }.asSingle
            )
        }
    }
}
