package at.asitplus.wallet.backend.service

import at.asitplus.wallet.lib.agent.Issuer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RevocationListWriter(
    private val issuer: Issuer
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun writeRevocationList(timePeriod: Int) {
        log.info("Writing revocation list for $timePeriod")
        val revocationList = issuer.buildRevocationList(timePeriod)
        // TODO actually write revocation list to file
    }

}