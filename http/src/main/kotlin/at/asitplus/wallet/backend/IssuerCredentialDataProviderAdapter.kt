package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import org.slf4j.LoggerFactory
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider].
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val credentialDataProvider: CredentialDataProvider,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(
        subjectId: String,
        attributeName: String
    ): IssuerCredentialDataProvider.CredentialToBeIssued? {
        val bpk = getVerifiedDeviceBinding(subjectId) ?: return null
        val subject = credentialDataProvider.getClaim(subjectId, attributeName, bpk)
        return subject?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(it, lifetime, ConstantIndex.Generic.vcType)
        }
    }

    override fun getCredential(
        subjectId: String,
        attributeType: String
    ): IssuerCredentialDataProvider.CredentialToBeIssued? {
        val bpk = getVerifiedDeviceBinding(subjectId) ?: return null
        val subject = credentialDataProvider.getCredential(subjectId, attributeType, bpk)
        return subject?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(it, lifetime, attributeType)
        }
    }

    private fun getVerifiedDeviceBinding(subjectId: String): String? {
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
        return deviceBinding.bpk
    }

}