package at.asitplus.wallet.backend.config

import at.asitplus.openid.CredentialRequestParameters
import at.asitplus.openid.CredentialRequestProofContainer
import at.asitplus.openid.OpenIdConstants
import at.asitplus.signum.indispensable.josef.KeyAttestationJwt
import at.asitplus.signum.indispensable.josef.KeyStorageStatus
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.ProofValidator
import at.asitplus.wallet.lib.etsi.LoTEStage
import at.asitplus.wallet.lib.etsi.LoteProfile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class WalletAttestationConfigurationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `disabled client authentication preserves existing behavior`() {
        runBlocking {
            walletClientAuthenticationService(
                WalletAttestationConfiguration(), "https://issuer.example",
                WalletProviderTrustService(WalletAttestationConfiguration(), DefaultResourceLoader()),
            ).authenticateClient(null, null, null)
        }
    }

    @Test
    fun `enabled client authentication rejects missing attestation`() {
        runBlocking {
            walletClientAuthenticationService(
                    WalletAttestationConfiguration(enabled = true), "https://issuer.example",
                    WalletProviderTrustService(WalletAttestationConfiguration(enabled = true), DefaultResourceLoader()),
                ).authenticateClient(null, null, null).exceptionOrNull().shouldBeInstanceOf<OAuth2Exception.InvalidClient>()
        }
    }

    @Test
    fun `configured certificate is loaded and only that signer is trusted`() {
        runBlocking {
            val trusted = EphemeralKeyWithSelfSignedCert().getCertificate()!!
            val other = EphemeralKeyWithSelfSignedCert().getCertificate()!!
            val location = tempDir.resolve("wallet-provider.pem")
            val pem = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(trusted.encodeToDer())
            location.writeText("-----BEGIN CERTIFICATE-----\n$pem\n-----END CERTIFICATE-----\n")

            val service = walletClientAuthenticationService(
                WalletAttestationConfiguration(enabled = true, trustedCertificates = listOf(location.toUri().toString())),
                "https://issuer.example", WalletProviderTrustService(
                    WalletAttestationConfiguration(enabled = true, trustedCertificates = listOf(location.toUri().toString())),
                    DefaultResourceLoader(),
                ),
            )
            service.authenticateClient(null, null, null).exceptionOrNull().shouldBeInstanceOf<OAuth2Exception.InvalidClient>()

            val anchor = CertificateFactory.getInstance("X.509")
                .generateCertificate(trusted.encodeToDer().inputStream()) as X509Certificate
            isTrustedAttestationChain(listOf(trusted), setOf(anchor)) shouldBe true
            isTrustedAttestationChain(listOf(other), setOf(anchor)) shouldBe false
            isTrustedAttestationChain(listOf(trusted), emptySet()) shouldBe false
        }
    }

    @Test
    fun `enabled client authentication fails startup for unreadable trust resource`() {
        shouldThrow<Exception> {
            walletClientAuthenticationService(
                WalletAttestationConfiguration(
                    enabled = true,
                    trustedCertificates = listOf(tempDir.resolve("missing.pem").toUri().toString()),
                ),
                "https://issuer.example", WalletProviderTrustService(
                    WalletAttestationConfiguration(
                        enabled = true,
                        trustedCertificates = listOf(tempDir.resolve("missing.pem").toUri().toString()),
                    ),
                    DefaultResourceLoader(),
                ),
            )
        }
    }

    @Test
    fun `key attestation requirement is advertised only when wallet attestation is enabled`() {
        val resources = DefaultResourceLoader()
        ProofValidator(publicContext = "https://issuer.example")
            .validProofTypes().values.forEach { it.keyAttestationRequired.shouldBeNull() }
        credentialProofValidator(
            WalletAttestationConfiguration(), "https://issuer.example",
            WalletProviderTrustService(WalletAttestationConfiguration(), resources),
        ).validProofTypes().values.forEach { it.keyAttestationRequired.shouldBeNull() }
        credentialProofValidator(
            WalletAttestationConfiguration(enabled = true), "https://issuer.example",
            WalletProviderTrustService(WalletAttestationConfiguration(enabled = true), resources),
        ).validProofTypes().values.forEach { it.keyAttestationRequired.shouldNotBeNull() }
    }

    @Test
    fun `key attestation is accepted only from a trusted Wallet Provider in wallet attestation mode`() {
        runBlocking {
            val trustedProvider = EphemeralKeyWithSelfSignedCert()
            val configuration = WalletAttestationConfiguration(
                enabled = true,
                trustedKeyAttestationCertificates = listOf(writePem("ka-anchor.pem", trustedProvider)),
            )
            val validator = credentialProofValidator(
                configuration, "https://issuer.example",
                WalletProviderTrustService(configuration, DefaultResourceLoader(), fetch = { error("offline") }),
            )
            val attestedKey = EphemeralKeyWithoutCert()

            validator.validateAttestation(trustedProvider, attestedKey) shouldBe
                    listOf(attestedKey.jsonWebKey.toCryptoPublicKey().getOrThrow())
            shouldThrow<OAuth2Exception.InvalidProof> {
                validator.validateAttestation(EphemeralKeyWithSelfSignedCert(), attestedKey)
            }
        }
    }

    @Test
    fun `optional key attestation only needs a valid signature without wallet attestation mode`() {
        runBlocking {
            val configuration = WalletAttestationConfiguration()
            val validator = credentialProofValidator(
                configuration, "https://issuer.example",
                WalletProviderTrustService(configuration, DefaultResourceLoader(), fetch = { error("offline") }),
            )
            val attestedKey = EphemeralKeyWithoutCert()

            validator.validateAttestation(EphemeralKeyWithSelfSignedCert(), attestedKey) shouldBe
                    listOf(attestedKey.jsonWebKey.toCryptoPublicKey().getOrThrow())
        }
    }

    private suspend fun writePem(name: String, keyMaterial: EphemeralKeyWithSelfSignedCert): String {
        val location = tempDir.resolve(name)
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(keyMaterial.getCertificate()!!.encodeToDer())
        location.writeText("-----BEGIN CERTIFICATE-----\n$pem\n-----END CERTIFICATE-----\n")
        return location.toUri().toString()
    }

    private suspend fun ProofValidator.validateAttestation(signer: KeyMaterial, attestedKey: KeyMaterial) =
        validateProofExtractSubjectPublicKeys(
            CredentialRequestParameters(
                proofs = CredentialRequestProofContainer(
                    attestation = setOf(keyAttestation(signer, attestedKey, nonce().clientNonce).jws)
                )
            )
        )

    private suspend fun keyAttestation(signer: KeyMaterial, attestedKey: KeyMaterial, nonce: String) =
        SignJwt<KeyAttestationJwt>(signer, JwsHeaderCertOrJwk())(
            type = OpenIdConstants.KEY_ATTESTATION_JWT_TYPE,
            payload = KeyAttestationJwt(
                issuedAt = Clock.System.now(),
                expiration = Clock.System.now() + 1.days,
                attestedKeys = setOf(attestedKey.jsonWebKey),
                nonce = nonce,
                keyStorage = setOf("iso_18045_high"),
                userAuthentication = setOf("iso_18045_high"),
                certification = "https://example.org/certification/wscd",
                keyStorageStatus = KeyStorageStatus(
                    status = buildJsonObject {
                        putJsonObject("status_list") {
                            put("idx", 7)
                            put("uri", "https://example.org/status/key-storage")
                        }
                    },
                    expiration = Clock.System.now() + 31.days,
                ),
            ),
            serializer = KeyAttestationJwt.serializer(),
        ).getOrThrow()

    @Test
    fun `wallet trust loads both LoTE stages and retains the A-SIT root when lists are unavailable`() {
        runBlocking {
            val fetched = mutableListOf<String>()
            val service = WalletProviderTrustService(
                WalletAttestationConfiguration(enabled = true),
                DefaultResourceLoader(),
                fetch = { url -> fetched += url; "invalid JWS" },
            )

            val anchors = service.walletProviderAnchors()
            fetched shouldBe listOf(
                LoTEStage.ACCEPTANCE.fetchUrl(LoteProfile.WALLET),
                LoTEStage.DEVELOPMENT.fetchUrl(LoteProfile.WALLET),
            )
            anchors.single().subjectX500Principal.name.contains("O=A-SIT") shouldBe true
            service.keyAttestationAnchors() shouldBe anchors
            service.walletProviderAnchors()
            fetched.size shouldBe 2 // failed lists are retried later, not on every request
        }
    }
}
