package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.CredentialSubject
import org.slf4j.LoggerFactory
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider]
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val credentialDataProvider: CredentialDataProvider,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }

        if (deviceBinding.keyId != subjectId)
            return null.also {
                log.error(
                    "Got invalid keyId ('{}') from authenticated user when trying to issue credentials for ('{}')",
                    deviceBinding.keyId, subjectId
                )
            }

        return credentialDataProvider.getClaim(subjectId, attributeName, deviceBinding.bpk)
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }

        if (deviceBinding.keyId != subjectId)
            return null.also {
                log.error(
                    "Got invalid keyId ('{}') from authenticated user when trying to issue credentials for ('{}')",
                    deviceBinding.keyId, subjectId
                )
            }

        return credentialDataProvider.getCredential(subjectId, attributeType, deviceBinding.bpk)
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

}