package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.EcoConnectionTest.UserDetailsServiceInt.certificate
import at.asitplus.wallet.backend.auth.AuthenticatedDeviceBindingUser
import at.asitplus.wallet.backend.auth.ExtNonceAuthnService
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMinLength
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.random.Random

@Disabled("Would need a valid API-Key in 'application-eco.yml'")
@ActiveProfiles(profiles = ["eco", "pupilid"])
@SpringBootTest(classes = [EcoConnectionTest.TestConfig::class])
class EcoConnectionTest {

    @Autowired
    private lateinit var extNonceAuthnService: ExtNonceAuthnService

    @Autowired
    private lateinit var issuerCredentialDataProvider: IssuerCredentialDataProvider


    /**
     * Workaround to be able to read the random [certificate] (for other mock beans),
     * that will be used in the user details of the authenticated user.
     */
    private object UserDetailsServiceInt : UserDetailsService {

        val bpk = UUID.randomUUID().toString()
        val certificate: ByteArray = Random.nextBytes(32)

        override fun loadUserByUsername(username: String): UserDetails {
            return AuthenticatedDeviceBindingUser(bpk, certificate)
        }
    }

    /**
     * Class needed to define a bean called [userDetailsServiceInt] that
     * can be picked up by the [WithUserDetails] annotation in a test case
     */
    @TestConfiguration
    internal class TestConfig {
        @Bean
        fun userDetailsServiceInt(): UserDetailsService {
            return UserDetailsServiceInt
        }
    }

    @Test
    fun extNonceService() {
        val nonce = extNonceAuthnService.generateNonce()?.nonce
        nonce.shouldNotBeNull()
        nonce shouldHaveMinLength 8

        val bpk = extNonceAuthnService.exchangeNonceForBpk(nonce)
        bpk shouldHaveMinLength 8

        val success = extNonceAuthnService.invalidateNonce(nonce)
        success shouldBe true
    }

    @Test
    @WithUserDetails(userDetailsServiceBeanName = "userDetailsServiceInt")
    fun credentialDataProvider() {
        val subjectId = UUID.randomUUID().toString()
        val credential = issuerCredentialDataProvider.getCredential(subjectId, ConstantIndex.PupilId.vcType)

        credential.shouldNotBeNull()
    }

}