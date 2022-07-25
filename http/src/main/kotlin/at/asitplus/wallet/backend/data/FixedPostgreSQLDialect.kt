package at.asitplus.wallet.backend.data

import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor
import java.sql.Types


class FixedPostgreSQLDialect : PostgreSQL10Dialect() {
    init {
        registerColumnType(Types.BLOB, "bytea")
    }

    override fun remapSqlTypeDescriptor(sqlTypeDescriptor: SqlTypeDescriptor): SqlTypeDescriptor? =
        when (sqlTypeDescriptor.sqlType) {
            Types.BLOB -> BinaryTypeDescriptor.INSTANCE
            else -> super.remapSqlTypeDescriptor(sqlTypeDescriptor)
        }
}