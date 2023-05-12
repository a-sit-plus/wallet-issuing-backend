package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.data.CredentialDataProvider.CredentialToBeIssuedAttachment
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.keyId
import at.asitplus.wallet.lib.AuthenticationError
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import kotlin.time.Duration


/**
 * Implements interface from VC Library to wrap calls to a specific [CredentialDataProvider].
 */
class IssuerCredentialDataProviderAdapter(
    private val lifetime: Duration,
    private val credentialDataProvider: CredentialDataProvider,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    override fun getCredentialWithType(
        subjectId: String,
        attributeTypes: Collection<String>
    ): KmmResult<List<IssuerCredentialDataProvider.CredentialToBeIssued>> {
        val deviceBinding = getVerifiedDeviceBinding(subjectId)
        val maxExpiration = Clock.System.now() + lifetime
        val cappedExpiration = if (deviceBinding != null) {
            val bindingExpiration = deviceBinding.validUntil.toKotlinInstant()
            if (maxExpiration > bindingExpiration) bindingExpiration else maxExpiration
        } else {
            maxExpiration
        }
        val credential = credentialDataProvider.getCredentialWithType(
            subjectId,
            attributeTypes,
            deviceBinding?.bpk,
            cappedExpiration,
        )

        return credential.map { list ->
            list.map {
                IssuerCredentialDataProvider.CredentialToBeIssued(
                    it.subject,
                    it.expiration,
                    it.attributeType,
                    it.attachments.map(CredentialToBeIssuedAttachment::toIssuerCredentialDataProviderFormat),
                )
            }
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
