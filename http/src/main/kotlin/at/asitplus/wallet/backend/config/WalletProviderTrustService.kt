package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.toJcaCertificateBlocking
import at.asitplus.wallet.lib.etsi.LoteProfile
import org.springframework.core.io.ResourceLoader
import java.security.cert.X509Certificate
import kotlin.time.Clock

/** Wallet-specific compatibility anchors layered on the shared, profile-scoped LoTE service. */
class WalletProviderTrustService(
    private val configuration: WalletAttestationConfiguration,
    resourceLoader: ResourceLoader,
    private val trustLists: TrustListService,
) {
    constructor(
        configuration: WalletAttestationConfiguration,
        resourceLoader: ResourceLoader,
        fetch: suspend (String) -> String = ::fetchTrustList,
        clock: Clock = Clock.System,
    ) : this(configuration, resourceLoader, TrustListService(
        TrustListConfiguration().withWalletOverrides(configuration), resourceLoader, fetch, clock,
    ))

    private val extras = loadAttestationAnchors(configuration.trustedCertificates, resourceLoader)
    private val keyExtras = loadAttestationAnchors(configuration.trustedKeyAttestationCertificates, resourceLoader)

    suspend fun walletProviderAnchors(): Set<X509Certificate> =
        if (LoteProfile.WALLET !in trustLists.urlsByProfile) emptySet()
        else trustLists.issuanceAnchors(LoteProfile.WALLET).map { it.toJcaCertificateBlocking().getOrThrow() }.toSet() + extras

    suspend fun keyAttestationAnchors(): Set<X509Certificate> =
        if (LoteProfile.WALLET !in trustLists.urlsByProfile) emptySet()
        else walletProviderAnchors() + keyExtras

    suspend fun statusAnchors() = trustLists.revocationAnchors(LoteProfile.WALLET)
}
