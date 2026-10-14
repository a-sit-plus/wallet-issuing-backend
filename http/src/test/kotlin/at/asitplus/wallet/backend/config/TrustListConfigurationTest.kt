package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

class TrustListConfigurationTest {
    @Test
    fun `Spring binds profile names stages and per-profile certificate maps`() {
        ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration::class.java)
            .withPropertyValues(
                "backend.trust-lists.profiles[0]=WALLET", "backend.trust-lists.profiles[1]=PID",
                "backend.trust-lists.stages[0]=ACCEPTANCE",
                "backend.trust-lists.trusted-certificates.WALLET.issuance[0]=file:/wallet-issuance.pem",
                "backend.trust-lists.trusted-certificates.WALLET.status[0]=file:/wallet-status.pem",
                "backend.trust-lists.urls.PID[0]=https://example.org/pid.json",
            ).run { context ->
                check(context.startupFailure == null) { context.startupFailure.toString() }
                val config = context.getBean(BackendConfigurationProperties::class.java).trustLists
                config.profiles shouldBe setOf(LoteProfile.WALLET, LoteProfile.PID)
                config.stages shouldBe listOf(LoTEStage.ACCEPTANCE)
                config.trustedCertificates.getValue(LoteProfile.WALLET) shouldBe
                    TrustListCertificates(listOf("file:/wallet-issuance.pem"), listOf("file:/wallet-status.pem"))
                config.urls.getValue(LoteProfile.PID) shouldBe listOf("https://example.org/pid.json")
            }
    }

    @Test
    fun `unknown profile is a configuration error`() {
        ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration::class.java)
            .withPropertyValues("backend.trust-lists.profiles[0]=TYPO").run { check(it.startupFailure != null) }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BackendConfigurationProperties::class)
    @Import(LoteProfileConverter::class)
    class PropertiesConfiguration
}
