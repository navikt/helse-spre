package no.nav.helse.spre.gosys.vedtakFattet

import javax.sql.DataSource
import kotliquery.TransactionalSession
import kotliquery.sessionOf

class SessionFactory(val dataSource: DataSource) {
    inline fun <T> transactionally(block: context(TransactionalSession) () -> T): T =
        sessionOf(dataSource).use {
            it.transaction { transactionalSession ->
                block(transactionalSession)
            }
        }
}
