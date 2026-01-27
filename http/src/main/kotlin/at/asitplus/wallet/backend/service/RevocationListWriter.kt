package at.asitplus.wallet.backend.service

import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.communication.primitives.StatusListTokenMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.moveTo
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@Service
class RevocationListWriter(
    private val statusListIssuer: StatusListIssuer,
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
                    Path(jwtPath).createDirectories()
                    writeStatusListJwt(Path(jwtPath, timePeriod.toString()), timePeriod, statusListIssuer)
                    Path(cwtPath).createDirectories()
                    writeStatusListCwt(Path(cwtPath, timePeriod.toString()), timePeriod, statusListIssuer)
                }
            }
        }
    }

    private suspend fun writeStatusListJwt(
        destinationFile: Path,
        timePeriod: Int,
        statusListIssuer: StatusListIssuer,
    ) {
        val token = statusListIssuer.provideStatusListToken(listOf(StatusListTokenMediaType.Jwt))
        val content = token.second as StatusListJwt
        val text = content.value.serialize()
        createTempFile().apply {
            writeText(text)
            moveTo(destinationFile, true)
        }
        log.info("Wrote JWT status token for $timePeriod to ${destinationFile.pathString} with ${text.length} chars")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun writeStatusListCwt(
        destinationFile: Path,
        timePeriod: Int,
        statusListIssuer: StatusListIssuer,
    ) {
        val token = statusListIssuer.provideStatusListToken(listOf(StatusListTokenMediaType.Cwt))
        val content = token.second as StatusListCwt
        val bytes = coseCompliantSerializer.encodeToByteArray(content.value)
        createTempFile().apply {
            writeBytes(bytes)
            moveTo(destinationFile, true)
        }
        log.info("Wrote CWT status token for $timePeriod to ${destinationFile.pathString} with ${bytes.size} bytes")
    }

}