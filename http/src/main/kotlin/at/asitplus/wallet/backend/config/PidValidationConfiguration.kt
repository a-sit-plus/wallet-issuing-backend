package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.agent.TrustedCertificates
import at.asitplus.wallet.lib.agent.validation.StatusListTokenResolver
import at.asitplus.wallet.lib.etsi.LoteProfile
import at.asitplus.wallet.lib.validation.CredentialTrustAnchors
import at.asitplus.wallet.lib.validation.CredentialTrustScope
import at.asitplus.wallet.lib.validation.CredentialTrustScopes
import at.asitplus.wallet.lib.validation.CredentialValidator
import at.asitplus.wallet.lib.validation.StatusPolicy
import at.asitplus.wallet.lib.validation.StatusValidator
import at.asitplus.wallet.lib.validation.TrustPolicy
import at.asitplus.wallet.lib.validation.ValidationPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.minutes

/** Trust and validation policy for PID login; Wallet Provider anchors never authorize PID credentials. */
@Configuration(proxyBeanMethods = false)
class PidValidationConfiguration {
    @Bean
    fun pidTrustAnchors(trustLists: TrustListService): CredentialTrustAnchors = CredentialTrustScopes(
        CredentialTrustScope(
            credentialIdentifiers = CredentialCatalog.entries.filter {
                LoteProfile.fromSchemeIdentifier(it.identifier) == LoteProfile.PID
            }.map { it.identifier }.toSet(),
            source = "PID: ${trustLists.urlsByProfile[LoteProfile.PID].orEmpty().joinToString()}, configured PID anchors",
            issuance = TrustedCertificates { trustLists.issuanceAnchors(LoteProfile.PID) },
            status = TrustedCertificates { trustLists.revocationAnchors(LoteProfile.PID) },
        ),
    )

    @Bean
    fun pidCredentialValidator(pidTrustAnchors: CredentialTrustAnchors, statusListTokenResolver: StatusListTokenResolver) =
        CredentialValidator(trustAnchors = pidTrustAnchors, statusValidator = StatusValidator(statusListTokenResolver))

    @Bean
    fun pidPresentationPolicy() = ValidationPolicy(
        trust = TrustPolicy.RequireAuthorizedSigner,
        status = StatusPolicy.ValidateIfPresent(TrustPolicy.RequireAuthorizedSigner),
        timeLeeway = 5.minutes,
        requireTimeliness = true,
    )
}
