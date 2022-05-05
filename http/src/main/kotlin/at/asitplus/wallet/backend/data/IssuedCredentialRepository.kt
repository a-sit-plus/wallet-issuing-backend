package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IssuedCredentialRepository : JpaRepository<IssuedCredential, Long> {

    fun findByVcId(vcId: String): IssuedCredential?

    fun findAllByRevokedFalse(): Collection<IssuedCredential>

    @Query("select i.revocationListIndex from IssuedCredential i where i.revoked = true order by i.revocationListIndex")
    fun getRevocationListIndexByRevokedTrueOrdered(): Collection<Long>

    fun findByRevokedFalseAndDeviceBinding_Bpk(bpk: String): Collection<IssuedCredential>

    fun findByRevokedFalseAndDeviceBinding_BpkAndDeviceBinding_DeviceId(
        bpk: String,
        deviceId: String
    ): Collection<IssuedCredential>

    @Query("select max(i.revocationListIndex) from IssuedCredential i")
    fun getMaxRevocationListIndex(): Long?


}