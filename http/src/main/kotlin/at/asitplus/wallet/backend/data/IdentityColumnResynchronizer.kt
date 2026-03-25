package at.asitplus.wallet.backend.data

import io.github.aakira.napier.Napier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class IdentityColumnResynchronizer(
    private val jdbcTemplate: JdbcTemplate,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun resynchronizeCredentialTablesOnStartup() {
        listOf("prepared_credential", "issued_credential").forEach(::resynchronize)
    }

    fun resynchronize(tableName: String, columnName: String = "id") {
        runCatching {
            val nextId = jdbcTemplate.queryForObject(
                "select coalesce(max($columnName), 0) + 1 from $tableName",
                Long::class.java,
            ) ?: 1L
            when (databaseProductName()) {
                "h2", "postgresql" ->
                    jdbcTemplate.execute("alter table $tableName alter column $columnName restart with $nextId")

                else -> Napier.w("Skipping identity resynchronization for unsupported database on $tableName.$columnName")
            }
            Napier.i("Resynchronized identity column for $tableName.$columnName to start with next value")
        }.onFailure { exception ->
            Napier.w("Could not resynchronize identity column for $tableName.$columnName", exception)
        }
    }

    private fun databaseProductName(): String? = try {
        jdbcTemplate.dataSource?.connection?.use { connection ->
            connection.metaData.databaseProductName.lowercase()
        }
    } catch (_: Exception) {
        null
    }
}
