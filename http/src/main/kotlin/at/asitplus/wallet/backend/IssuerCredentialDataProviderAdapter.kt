package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds


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
        val credential = credentialDataProvider.getClaim(subjectId, attributeName, bpk, lifetime)
        return credential?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(
                it.subject,
                it.lifetime.toSeconds().seconds,
                it.attributeType
            )
        }
    }

    override fun getCredential(
        subjectId: String,
        attributeType: String
    ): IssuerCredentialDataProvider.CredentialToBeIssued? {
        val bpk = getVerifiedDeviceBinding(subjectId) ?: return null
        val credential = credentialDataProvider.getCredential(subjectId, attributeType, bpk, lifetime)
        return credential?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(
                it.subject,
                it.lifetime.toSeconds().seconds,
                it.attributeType
            )
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