package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

internal class V28__uppercase_enum_verdier: BaseJavaMigration() {
    override fun migrate(context: Context?) {
        // 1. select * from tingen where versjon=0.0.1
        // 2. forEach { row ->
        //  2.1 if (row.siste) insertNyRad(row.fix, true)
        //  2.2 else insertNyRad(row.fix, false)
        //  2.3 update(row, false)

        // TODOS:
        //  1. Fjern unntakshåndteringen i DAO med uppercase()
        //  2. Tenk en peu på korrigeringer av korrigeringer
    }
}