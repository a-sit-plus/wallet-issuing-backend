package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.data.StatusListToken
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.communication.primitives.StatusListTokenMediaType
import at.asitplus.wallet.lib.iso.vckCborSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
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
    @OptIn(ExperimentalSerializationApi::class)
    fun writeRevocationList(timePeriod: Int) {
        Dispatchers.IO.run {
            runBlocking {
                with(configurationProperties.revocationList) {
                    log.info("Writing revocation list for $timePeriod")
                    Path(jwtPath).createDirectories()
                    Path(jwtPath, timePeriod.toString()).let { destinationFile ->
                        issuer.provideStatusListToken(listOf(StatusListTokenMediaType.Jwt)).let { token ->
                            val content = token.second as StatusListToken.StatusListJwt
                            val text = content.value.serialize()
                            createTempFile().apply {
                                writeText(text)
                                moveTo(destinationFile, true)
                            }
                            log.info("Wrote JWT status token for $timePeriod to ${destinationFile.pathString} with ${text.length} chars")
                        }
                    }
                    Path(cwtPath).createDirectories()
                    Path(cwtPath, timePeriod.toString()).let { destinationFile ->
                        issuer.provideStatusListToken(listOf(StatusListTokenMediaType.Cwt)).let { token ->
                            val content = token.second as StatusListToken.StatusListCwt
                            val bytes = vckCborSerializer.encodeToByteArray(content.value)
                            createTempFile().apply {
                                writeBytes(bytes)
                                moveTo(destinationFile, true)
                            }
                            log.info("Wrote CWT status token for $timePeriod to ${destinationFile.pathString} with ${bytes.size} bytes")
                        }
                    }
                }
            }
        }
    }

}