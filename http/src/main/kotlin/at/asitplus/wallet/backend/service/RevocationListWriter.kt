package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.vckJsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.io.path.*

@Service
class RevocationListWriter(
    private val issuer: Issuer,
    private val configurationProperties: BackendConfigurationProperties,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * We don't want to disturb the reading process of revocation lists,
     * so we'll write to a temporary file first, and move to the final destination.
     * Since move is an atomic operation (at least on Linux), the read
     * operations should never read a partial file.
     */
    fun writeRevocationList(timePeriod: Int) {
        Dispatchers.IO.run {
            runBlocking {
                log.info("Writing revocation list for $timePeriod")
                Path(configurationProperties.revocationList.path).createDirectories()
                val destinationFile = Path(configurationProperties.revocationList.path, timePeriod.toString())
                issuer.buildStatusList(timePeriod)?.let { list ->
                    val text = vckJsonSerializer.encodeToString(list)
                    createTempFile().apply {
                        writeText(text)
                        moveTo(destinationFile, true)
                    }
                    log.info("Wrote revocation list for $timePeriod to ${destinationFile.pathString} with ${text.length} chars")
                }
            }
        }
    }

}