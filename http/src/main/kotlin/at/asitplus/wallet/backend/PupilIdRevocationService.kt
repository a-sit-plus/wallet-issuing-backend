package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.backend.data.DeviceBinding
import at.asitplus.wallet.backend.data.DeviceBindingRepository
import at.asitplus.wallet.backend.data.IssuedCredential
import at.asitplus.wallet.backend.data.IssuedCredentialRepository
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.encodeBase64
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

interface PupilIdRevocationService {

    fun revokeCredentialsByBpk(bpk: String): Boolean

    fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Boolean

    fun revokeCredentialsByVcId(vcId: String): Boolean

    fun getDeviceBindingForCurrentUser(): DeviceBinding?

    fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int?

    fun isRevoked(vcId: String): Boolean?

    fun getAllNonRevokedWithDetails(): List<RevocationListInfo>

    fun getRevokedStatusListIndexList(): Collection<Int>
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

class DefaultPupilIdRevocationService(
    private val credentialRepo: IssuedCredentialRepository,
    private val deviceBindingRepo: DeviceBindingRepository,
) : PupilIdRevocationService {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun isRevoked(vcId: String): Boolean? {
        return credentialRepo.findByVcId(vcId)?.revoked
    }

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant
    ): Int? {
        if (credentialRepo.findByVcId(vcId) != null)
            return null.also {
                log.error("Tried to store a new credential for existing vcId '$vcId'")
            }
        val deviceBinding = getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to store vcId '$vcId'")
            }
        val exp = java.time.Instant.ofEpochMilli(expirationDate.toEpochMilliseconds())
        val issuedCredential = IssuedCredential(vcId, credentialSubject.id, exp, deviceBinding)
        val savedCredential = credentialRepo.save(issuedCredential)
        return savedCredential.revocationListIndex.toInt()
    }

    override fun revokeCredentialsByVcId(vcId: String): Boolean {
        val credential = credentialRepo.findByVcId(vcId) ?: return false
        return revokeAllCredentials(listOf(credential))
    }

    override fun revokeCredentialsByBpk(bpk: String): Boolean {
        val credentials = credentialRepo.findByRevokedFalseAndDeviceBinding_Bpk(bpk)
        return revokeAllCredentials(credentials)
    }

    override fun revokeCredentialsByBpkAndDeviceId(bpk: String, deviceId: String?): Boolean {
        if (deviceId == null)
            return revokeCredentialsByBpk(bpk)
        val credentials = credentialRepo.findByRevokedFalseAndDeviceBinding_BpkAndDeviceBinding_DeviceId(bpk, deviceId)
        return revokeAllCredentials(credentials)
    }

    private fun revokeAllCredentials(credentials: Collection<IssuedCredential>): Boolean {
        if (credentials.isEmpty())
            return false
        val toStore = mutableListOf<IssuedCredential>()
        credentials.forEach {
            it.revoked = true
            toStore.add(it)
        }
        toStore.forEach {
            credentialRepo.save(it)
        }
        return true
    }

    override fun getRevokedStatusListIndexList(): Collection<Int> {
        return credentialRepo.findAllByRevokedTrueOrderByRevocationListIndex()
            .map { it.revocationListIndex.toInt() }
    }

    override fun getDeviceBindingForCurrentUser(): DeviceBinding? {
        val principal = SecurityContextHolder.getContext()?.authentication?.principal
        if (principal !is AuthenticatedDeviceBindingUser)
            return null.also {
                log.error("Got no authenticated user when trying to store vc")
            }
        return deviceBindingRepo.findByCertificate(principal.certificate)
            ?: return null.also {
                log.error("Found no authenticated user for certificate '${principal.certificate.encodeBase64()}'")
            }
    }

    override fun getAllNonRevokedWithDetails(): List<RevocationListInfo> {
        return credentialRepo.findAllByRevokedFalse().map {
            RevocationListInfo(it.vcId, it.createdOn.toString(), "PupilId", it.subjectId)
        }
    }
}