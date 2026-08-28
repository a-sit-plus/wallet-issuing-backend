package at.asitplus.wallet.backend

import at.asitplus.iso.IssuerSigned
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.wallet.backend.config.StatusListGroups
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.oauth2.SimpleAuthorizationService
import at.asitplus.wallet.lib.oidvci.CredentialIssuer
import at.asitplus.wallet.lib.oidvci.WalletService.RequestOptions
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Verifies that `backend.credential-keys` actually routes the signing key: EU PID as mdoc gets its own key and its own
 * status list, while every other credential, EU PID as SD-JWT included, keeps the default key and the legacy status
 * list path.
 */
@OptIn(ExperimentalSerializationApi::class)
@SpringBootTest(properties = ["backend.credential-keys[eu.europa.ec.eudi.pid.1].type=MEMORY"])
class PerCredentialSigningKeyTest {

    @Autowired
    private lateinit var credentialIssuer: CredentialIssuer

    @Autowired
    private lateinit var authorizationServer: SimpleAuthorizationService

    @Autowired
    private lateinit var statusListGroups: StatusListGroups

    @Test
    fun `configured credential gets its own status list group`() {
        statusListGroups.all.map { it.slug } shouldBe listOf("", "eu.europa.ec.eudi.pid.1")
        statusListGroups.forCredential("eu.europa.ec.eudi.pid.1") shouldNotBe statusListGroups.default
        statusListGroups.forCredential("urn:eudi:pid:1") shouldBe statusListGroups.default
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun `configured credential is signed with its own key`() = runTest {
        val isoCertificates = issuePidIso().issuerAuth.unprotectedHeader.shouldNotBeNull()
            .certificateChain.shouldNotBeNull()
        val sdJwtCertificates = issuePidSdJwt().jwsHeader.certificateChain.shouldNotBeNull()

        isoCertificates.map { it.toList() } shouldNotBe sdJwtCertificates.map { it.encodeToDer().toList() }
    }

    @Test
    @WithOAuth2AuthenticationToken
    fun `configured credential points at its own status list`() = runTest {
        val isoStatusUri = issuePidIso().issuerAuth.payload.shouldNotBeNull()
            .status.shouldNotBeNull().uri.string
        val sdJwtPayload = issuePidSdJwt().plainPayload.decodeToString()

        isoStatusUri shouldContain "/credentials/status/eu.europa.ec.eudi.pid.1/"
        sdJwtPayload shouldContain "/credentials/status/"
        sdJwtPayload shouldNotContain "/credentials/status/eu.europa.ec.eudi.pid.1/"
    }

    @Test
    fun `every signing key is published in the issuer jwks`() {
        // a key missing here means wallets cannot verify credentials signed with it
        val published = credentialIssuer.jwtVcMetadata.jsonWebKeySet.shouldNotBeNull().keys
        published.size shouldBe statusListGroups.all.size
        statusListGroups.all.forEach { group ->
            published shouldContain group.keyMaterial.jsonWebKey
        }
    }

    private suspend fun issuePidIso(): IssuerSigned =
        coseCompliantSerializer.decodeFromByteArray(
            serialized(RequestOptions(isoScheme, ISO_MDOC)).decodeToByteArray(Base64())
        )

    private suspend fun issuePidSdJwt(): JwsCompact =
        JwsCompact(serialized(RequestOptions(sdJwtScheme, SD_JWT)).substringBefore("~"))

    private suspend fun serialized(requestOptions: RequestOptions): String =
        loadCredential(credentialIssuer, authorizationServer, requestOptions)
            .credentials.shouldNotBeNull().first().shouldNotBeNull()
            .credentialString.shouldNotBeNull()

    private val isoScheme
        get() = AttributeIndex.resolveIsoDoctype("eu.europa.ec.eudi.pid.1")
            ?: error("ISO mdoc scheme not resolved")

    private val sdJwtScheme
        get() = AttributeIndex.resolveSdJwtAttributeType("urn:eudi:pid:1")
            ?: error("SD-JWT scheme not resolved")
}
