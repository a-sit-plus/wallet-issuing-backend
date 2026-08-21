package at.asitplus.wallet.backend.service

import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.StatusListGroup
import at.asitplus.wallet.backend.config.StatusListGroups
import at.asitplus.wallet.lib.agent.StatusListIssuer
import at.asitplus.wallet.lib.data.StatusListCwt
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents.communication.primitives.StatusListTokenMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val statusListGroups: StatusListGroups,
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
    suspend fun writeRevocationList(timePeriod: Int) = withContext(Dispatchers.IO) {
        statusListGroups.all.forEach { write(it, timePeriod) }
    }

    /** Every group publishes the same revocation data, but signed with its own key and under its own path. */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun write(group: StatusListGroup, timePeriod: Int) {
        with(configurationProperties.revocationList) {
            Path(jwtPath(group.slug)).createDirectories()
            writeStatusListJwt(Path(jwtPath(group.slug), timePeriod.toString()), timePeriod, group.statusListAgent)
            Path(cwtPath(group.slug)).createDirectories()
            writeStatusListCwt(Path(cwtPath(group.slug), timePeriod.toString()), timePeriod, group.statusListAgent)
        }
    }

    private suspend fun writeStatusListJwt(
        destinationFile: Path,
        timePeriod: Int,
        statusListIssuer: StatusListIssuer,
    ) {
        val token = statusListIssuer.provideStatusListToken(listOf(StatusListTokenMediaType.Jwt))
        val content = token.second as StatusListJwt
        val text = content.value.jws.toString()
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