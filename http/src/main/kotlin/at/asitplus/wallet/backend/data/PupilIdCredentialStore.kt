package at.asitplus.wallet.backend.data

import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.lib.agent.IssuerCredentialStore
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.encodeBase64
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant


class PupilIdCredentialStore(
    private val repository: IssuedCredentialRepository,
    private val deviceBindingRepo: DeviceBindingRepository
) : IssuerCredentialStore {

    // TODO Besser das Interface nur als adapter implementieren

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun revoke(vcId: String): Boolean {
        val identifier = repository.findByVcId(vcId) ?: return false
        identifier.revoked = true
        repository.save(identifier)
        return true
    }

    // TODO Besser in eine Service-Klasse auslagern?
    fun revokeByBpk(): Boolean {
        //repository.findByDeviceBinding_Bpk()
        return false
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: kotlinx.datetime.Instant,
        expirationDate: kotlinx.datetime.Instant,
    ): Int? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null.also {
                log.error("Got no authenticated user when trying to store vcId '$vcId'")
            }
        val deviceBinding = deviceBindingRepo.findByCertificate(principal.certificate)
            ?: return null.also {
                log.error("Found no authenticated user for certificate '${principal.certificate.encodeBase64()}'")
            }
        if (repository.findByVcId(vcId) != null)
            return null.also {
                log.error("Tried to store a new credential for existing vcId '$vcId'")
            }
        val exp = Instant.ofEpochMilli(expirationDate.toEpochMilliseconds())
        val issuedCredential = IssuedCredential(vcId, credentialSubject.id, exp, deviceBinding)
        val savedCredential = repository.save(issuedCredential)
        return savedCredential.revocationListIndex.toInt()
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return repository.findAllByRevokedTrueOrderByRevocationListIndex()
            .map { it.revocationListIndex.toInt() }
    }

    fun isRevoked(vcId: String): Boolean? {
        return repository.findByVcId(vcId)?.revoked
    }

    fun getAllNonRevokedWithDetails(): List<RevocationListInfo> {
        return repository.findAllByRevokedFalse().map {
            RevocationListInfo(it.vcId, it.createdOn.toString(), "PupilId", it.subjectId)
        }
    }

    /**
     * Used in "revoke_list.html"
     */
    data class RevocationListInfo(
        val vcId: String,
        val issuanceDate: String,
        val attributeName: String,
        val subjectId: String
    )
}