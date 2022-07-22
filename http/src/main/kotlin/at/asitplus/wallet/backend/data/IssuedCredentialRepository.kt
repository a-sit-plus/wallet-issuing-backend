package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.Year

@Repository
interface IssuedCredentialRepository : JpaRepository<IssuedCredential, Long> {

    fun findBytimePeriodAndVcId(timePeriod: Year, vcId: String): IssuedCredential?

    fun findAllByRevokedFalseAndValidUntilAfter(validUntil: Instant): Collection<IssuedCredential>


    @Query("select i.revocationListIndex from IssuedCredential i where i.revoked = true and i.timePeriod = ?1 order by i.revocationListIndex")
    fun getRevocationListIndexByRevokedTrueOrdered(timePeriod: Year): Collection<Long>


    fun findByRevokedFalseAndValidUntilAfterAndDeviceBinding_Bpk(
        validUntil: Instant,
        bpk: String
    ): Collection<IssuedCredential>

    fun findByRevokedFalseAndValidUntilAfterAndDeviceBinding_BpkAndDeviceBinding_DeviceId(
        validUntil: Instant,
        bpk: String,
        deviceId: String
    ): Collection<IssuedCredential>

    @Query("select max(i.revocationListIndex) from IssuedCredential i")
    fun getMaxRevocationListIndex(): Long?

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<IssuedCredential>



}