package no.nav.helse.spre.gosys.vedtakFattet

import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

class SessionFactory(
    val dataSource: DataSource,
) {
    inline fun <T> transactionally(block: context(TransactionalSession) () -> T): T =
        sessionOf(dataSource).use {
            it.transaction { transactionalSession ->
                block(transactionalSession)
            }
        }
}
