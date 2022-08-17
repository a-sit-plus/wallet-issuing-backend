package at.asitplus.wallet.backend.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IssuedCredentialRepository : JpaRepository<IssuedCredential, Long> {

    fun findBytimePeriodAndVcId(timePeriod: Int, vcId: String): IssuedCredential?

    fun findAllByValidUntilAfter(validUntil: Instant): Collection<IssuedCredential>

    fun findByValidUntilAfterAndDeviceBinding_Bpk(
        validUntil: Instant,
        bpk: String
    ): Collection<IssuedCredential>

    fun findByValidUntilAfterAndDeviceBinding_BpkAndDeviceBinding_DeviceId(
        validUntil: Instant,
        bpk: String,
        deviceId: String
    ): Collection<IssuedCredential>

    @Query("select max(i.revocationListIndex) from IssuedCredential i")
    fun getMaxRevocationListIndex(): Long?

    fun findAllByValidUntilBefore(cutoff: Instant): Collection<IssuedCredential>



}