package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.keyId
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import org.slf4j.LoggerFactory
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider].
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val credentialDataProvider: CredentialDataProvider,
    private val deviceBindingStorageService: DeviceBindingStorageService,
    private val gracePeriod: Duration,
    private val clock: Clock
) : IssuerCredentialDataProvider {


    override fun getClaim(
        subjectId: String,
        attributeName: String
    ): IssuerCredentialDataProvider.CredentialToBeIssued? {
        val deviceBinding = getVerifiedDeviceBinding(subjectId) ?: return null
        val bindingExpiration = deviceBinding.validUntil.toKotlinInstant()
        val maxExpiration = clock.now() + lifetime
        val cappedExpiration =
            if (maxExpiration > bindingExpiration) bindingExpiration else maxExpiration
        val credential = credentialDataProvider.getClaim(
            subjectId,
            attributeName,
            deviceBinding.bpk,
            cappedExpiration
        )
        return credential?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(
                it.subject,
                it.expiration,
                it.attributeType
            )
        }
    }

    override fun getCredential(
        subjectId: String,
        attributeType: String
    ): IssuerCredentialDataProvider.CredentialToBeIssued? {
        val deviceBinding = getVerifiedDeviceBinding(subjectId) ?: return null
        val bindingExpiration = deviceBinding.validUntil.toKotlinInstant()
        val maxExpiration = clock.now() + lifetime
        val cappedExpiration =
            if (maxExpiration > bindingExpiration) bindingExpiration else maxExpiration
        val credential = credentialDataProvider.getCredential(
            subjectId,
            attributeType,
            deviceBinding.bpk,
            cappedExpiration
        )
        return credential?.let {
            IssuerCredentialDataProvider.CredentialToBeIssued(
                it.subject,
                it.expiration + gracePeriod,
                it.attributeType
            )
        }
    }

    private fun getVerifiedDeviceBinding(subjectId: String): DeviceBinding? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                Napier.e("Got no authenticated user when trying to issue credentials")
            }

        if (deviceBinding.keyId != subjectId)
            return null.also {
                Napier.e("Got invalid keyId from authenticated user when trying to issue credentials")
                Napier.v("keyId: '${deviceBinding.keyId}', subjectId: '$subjectId'")
            }
        return deviceBinding
    }

}