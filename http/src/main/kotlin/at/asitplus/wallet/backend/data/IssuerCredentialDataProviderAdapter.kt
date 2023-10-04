package at.asitplus.wallet.backend.data

import at.asitplus.KmmResult
import at.asitplus.wallet.backend.data.CredentialDataProvider.CredentialToBeIssuedAttachment
import at.asitplus.wallet.backend.service.DeviceBindingStorageService
import at.asitplus.wallet.backend.service.jsonWebKey
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.cbor.CoseKey
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
        subjectPublicKey: CoseKey?,
        attributeTypes: Collection<String>
    ): KmmResult<List<CredentialToBeIssued>> {
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
            subjectPublicKey,
        )

        return credential.map { list ->
            list.map {
                when (it) {
                    is CredentialDataProvider.CredentialToBeIssued.Iso ->               CredentialToBeIssued.Iso(
                        it.issuerSignedItems, it.subjectPublicKey, it.expiration, it.attributeType
                    )

                    is CredentialDataProvider.CredentialToBeIssued.Vc ->               CredentialToBeIssued.Vc(
                        it.subject,
                        it.expiration,
                        it.attributeType,
                        it.attachments.map(CredentialToBeIssuedAttachment::toIssuerCredentialDataProviderFormat),
                    )

                }
            }
        }
    }

    private fun getVerifiedDeviceBinding(subjectId: String): DeviceBinding? {
        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                Napier.e("Got no authenticated user when trying to issue credentials")
            }

        if (deviceBinding.jsonWebKey?.keyId != subjectId
            && deviceBinding.jsonWebKey?.identifier != subjectId
            && deviceBinding.jsonWebKey?.jwkThumbprint != subjectId
        ) {
            return null.also {
                Napier.e("getVerifiedDeviceBinding: Key from device binding does not match subject")
                Napier.v("keyId: '${deviceBinding.jsonWebKey?.keyId}', jwkThumbprint: '${deviceBinding.jsonWebKey?.jwkThumbprint}', subjectId: '$subjectId'")
            }
        }
        return deviceBinding
    }

}
